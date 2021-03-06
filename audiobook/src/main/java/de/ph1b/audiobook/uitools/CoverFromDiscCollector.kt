package de.ph1b.audiobook.uitools

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import com.squareup.picasso.Picasso
import de.ph1b.audiobook.Book
import de.ph1b.audiobook.Chapter
import de.ph1b.audiobook.misc.FileRecognition
import e
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Class for retrieving covers from disc.
 *
 * @author Paul Woitaschek
 */
@Singleton class CoverFromDiscCollector
@Inject constructor(context: Context, private val activityManager: ActivityManager, private val imageHelper: ImageHelper) {

  private val picasso = Picasso.with(context)
  private val coverChangedSubject = PublishSubject.create<Long>()

  /** Find and stores covers for each book */
  fun findCovers(books: List<Book>) {
    books.forEach { book ->
      val coverFile = book.coverFile()
      if (!coverFile.exists()) {
        if (book.type === Book.Type.COLLECTION_FOLDER || book.type === Book.Type.SINGLE_FOLDER) {
          val root = File(book.root)
          if (root.exists()) {
            val images = root.walk().filter { FileRecognition.imageFilter.accept(it) }
            getCoverFromDisk(images.toList())?.let {
              imageHelper.saveCover(it, coverFile)
              picasso.invalidate(coverFile)
              coverChangedSubject.onNext(book.id)
              return@forEach
            }
          }
        }
        getEmbeddedCover(book.chapters)?.let {
          imageHelper.saveCover(it, coverFile)
          picasso.invalidate(coverFile)
          coverChangedSubject.onNext(book.id)
        }
      }
    }
  }

  /** emits the bookId of a cover that has changed */
  fun coverChanged(): Observable<Long> = coverChangedSubject.hide()

  /** Find the embedded cover of a chapter */
  @Throws(InterruptedException::class)
  private fun getEmbeddedCover(chapters: List<Chapter>): Bitmap? {
    chapters.forEachIndexed { index, (file) ->
      val cover = imageHelper.getEmbeddedCover(file)
      if (cover != null || index == 5) return cover
    }
    return null
  }

  /** Returns the first bitmap that could be parsed from an image file */
  @Throws(InterruptedException::class)
  private fun getCoverFromDisk(coverFiles: List<File>): Bitmap? {
    // if there are images, get the first one.
    val mi = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(mi)
    val dimen = imageHelper.smallerScreenSize
    // only read cover if its size is less than a third of the available memory
    coverFiles.filter { it.length() < (mi.availMem / 3L) }.forEach {
      try {
        return picasso.load(it)
          .resize(dimen, dimen)
          .onlyScaleDown()
          .centerCrop()
          .get()
      } catch (ex: IOException) {
        e(ex) { "Error when saving cover $it" }
      }
    }
    return null
  }
}