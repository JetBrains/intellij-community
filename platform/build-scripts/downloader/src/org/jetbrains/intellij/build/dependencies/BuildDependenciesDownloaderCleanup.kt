// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.listDirectory
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.logging.Logger

/**
 * Clean-up local download cache in two stages:
 * 1) mark old files by writing near them a marker file (.marked.for.cleanup)
 * 2) on the second run remove both marked and original old files.
 *
 *
 * Why two stage removing is required: suppose you're running some build scripts after a month of vacation.
 * Older downloaded files will be marked for deletion and then some of them will be used again.
 * Without the marking they would be removed and re-downloaded again, which we do not want.
 */
class BuildDependenciesDownloaderCleanup(private val myCachesDir: Path) {
  private val myLastCleanupMarkerFile: Path = myCachesDir.resolve(LAST_CLEANUP_MARKER_FILE_NAME)

  @Throws(IOException::class)
  fun runCleanupIfRequired(): Boolean {
    if (!isTimeForCleanup) {
      return false
    }

    // Update file timestamp mostly
    Files.writeString(myLastCleanupMarkerFile, LocalDateTime.now().toString())
    cleanupCachesDir()
    return true
  }

  private val isTimeForCleanup: Boolean
    get() = try {
      !Files.exists(myLastCleanupMarkerFile) ||
      Files.getLastModifiedTime(myLastCleanupMarkerFile).toMillis() < System.currentTimeMillis() - CLEANUP_EVERY_DURATION.toMillis()
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }

  @Throws(IOException::class)
  private fun cleanupCachesDir() {
    if (!Files.exists(myCachesDir)) {
      LOG.fine("Caches directory '$myCachesDir' is missing, skipping cleanup")
      return
    }
    check(Files.isDirectory(myCachesDir)) { "Caches directory '$myCachesDir' is not a directory" }
    val cacheFiles: Set<Path> = HashSet(listDirectory(myCachesDir))
    val maxTimeMs = MAXIMUM_ACCESS_TIME_AGE.toMillis()
    val currentTime = System.currentTimeMillis()
    for (file in cacheFiles) {
      if (file == myLastCleanupMarkerFile) {
        continue
      }
      val fileName = file.fileName.toString()
      if (fileName.endsWith(MARKED_FOR_CLEANUP_SUFFIX)) {
        val realFile = myCachesDir.resolve(fileName.substring(0, fileName.length - MARKED_FOR_CLEANUP_SUFFIX.length))
        if (!cacheFiles.contains(realFile)) {
          LOG.info("CACHE-CLEANUP: Removing orphan marker: $file")
          Files.delete(file)
        }
        continue
      }
      val markFile = myCachesDir.resolve(fileName + MARKED_FOR_CLEANUP_SUFFIX)
      val attrs = Files.readAttributes(file, BasicFileAttributes::class.java)
      val lastAccessTime = attrs.lastModifiedTime()
      if (lastAccessTime.toMillis() > currentTime - maxTimeMs) {
        if (cacheFiles.contains(markFile)) {
          // File was recently updated, un-mark it
          Files.delete(markFile)
        }
        continue
      }
      if (Files.exists(markFile)) {
        // File is old AND already marked for cleanup - delete
        LOG.info(
          "CACHE-CLEANUP: Deleting file/directory '$file': it's too old and marked for cleanup")

        // Renaming file to a temporary name to prevent deletion of currently opened files, just in case
        val toRemove = myCachesDir.resolve(fileName + ".toRemove." + UUID.randomUUID())
        try {
          Files.move(file, toRemove)
          MoreFiles.deleteRecursively(toRemove, RecursiveDeleteOption.ALLOW_INSECURE)
        }
        catch (t: Throwable) {
          val writer = StringWriter()
          t.printStackTrace(PrintWriter(writer))
          LOG.warning("""
  Unable to delete file '$file': ${t.message}
  $writer
  """.trimIndent())
        }
        Files.delete(markFile)
      }
      else {
        LOG.info(
          "CACHE-CLEANUP: Marking File '$file' for deletion, it'll be removed on the next cleanup run")
        Files.writeString(markFile, "")
      }
    }
  }

  companion object {
    private val LOG = Logger.getLogger(BuildDependenciesDownloaderCleanup::class.java.name)
    private val MAXIMUM_ACCESS_TIME_AGE = Duration.ofDays(22)
    private val CLEANUP_EVERY_DURATION = Duration.ofDays(1)
    const val LAST_CLEANUP_MARKER_FILE_NAME = ".last.cleanup.marker"
    private const val MARKED_FOR_CLEANUP_SUFFIX = ".marked.for.cleanup"
  }
}
