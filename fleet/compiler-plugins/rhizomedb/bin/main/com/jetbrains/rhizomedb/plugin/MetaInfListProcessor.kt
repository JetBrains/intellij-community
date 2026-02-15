package com.jetbrains.rhizomedb.plugin

import java.io.File
import java.io.IOException

class MetaInfListProcessor(
  private val pathName: String,
  private val readProvider: ((String) -> List<String>)? = null,
  private val writeProvider: ((String, Collection<String>) -> Unit)? = null
) {
  private val file = File(pathName)

  fun read(filterOutdated: (String) -> Boolean = { true }): List<String> {
    val lines = try {
      if (readProvider != null) {
        readProvider.invoke(pathName)
      } else {
        if (file.exists()) file.readLines() else emptyList()
      }
    }
    catch (e: IOException) {
      throw IOException("Cannot read file $file to update registrars list", e)
    }

    return lines.filter { row -> filterOutdated(row) }
  }

  /**
   * Process previous cache and merge with newly generated content.
   *
   * @param newContent new content to merge, will not be filtered by [checkPreviousContent]
   * @param checkPreviousContent filter to apply on content that is already in cache file, if it returns true, the row will be kept in the new version
   * @return merged content, without duplicates that was written to file
   */
  fun readAndUpdate(newContent: List<String>, checkPreviousContent: (String) -> Boolean = { true }): Set<String> {
    return (newContent + read(checkPreviousContent)).toSet()
      .also {
        writeOrDelete(it)
      }
  }

  fun writeOrDelete(dump: Collection<String>) {
    if (writeProvider != null) {
      writeProvider.invoke(pathName, dump)
      return
    }

    if (dump.isNotEmpty()) {
      try {
        file.parentFile.mkdirs()
        file.createNewFile()
        file.printWriter().use { out ->
          dump.forEach { entry ->
            out.print(entry)
            out.print("\n")
          }
        }
      }
      catch (e: IOException) {
        throw IOException("Cannot write into $file file", e)
      }
    }
    else {
      try {
        file.delete()
      }
      catch (e: IOException) {
        throw IOException("Cannot delete $file file with", e)
      }
    }
  }
}
