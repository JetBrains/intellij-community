// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.io.write
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.name
import kotlin.io.path.setLastModifiedTime

class BuildDependenciesDownloaderCleanupTest {
  @JvmField @Rule val tempDir = TemporaryDirectory()

  @Test
  fun isTimeForCleanup() {
    val cachesDir = tempDir.createDir()
    val markerFile = cachesDir.resolve(CacheDirCleanup.LAST_CLEANUP_MARKER_FILE_NAME)
    val cleanup = CacheDirCleanup(cachesDir)

    // No marker file
    Assert.assertTrue(cleanup.runCleanupIfRequired())

    // New marker file
    markerFile.write("")
    Assert.assertFalse(cleanup.runCleanupIfRequired())

    // Old marker file, 90000 (~1+ day) seconds old
    markerFile.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis() - 90000 * 1000))
    Assert.assertTrue(cleanup.runCleanupIfRequired())
  }

  @Test
  fun cleanupOrphanMarkerFiles() {
    val cachesDir = tempDir.createDir()
    val cleanup = CacheDirCleanup(cachesDir)

    val dir1 = cachesDir.resolve("dir1")
    Files.createDirectories(dir1)
    cachesDir.resolve("xxx.marked.for.cleanup").write("")
    Assert.assertEquals("dir1\nxxx.marked.for.cleanup", listDir(cachesDir))

    Assert.assertTrue(cleanup.runCleanupIfRequired())

    Assert.assertEquals("dir1", listDir(cachesDir))
  }

  @Test
  fun `do not remove marked files if they were recently updated`() {
    val cachesDir = tempDir.createDir()
    val cleanup = CacheDirCleanup(cachesDir)

    val dir1 = cachesDir.resolve("dir1")
    Files.createDirectories(dir1)
    cachesDir.resolve("dir1.marked.for.cleanup").write("")

    Assert.assertEquals("dir1\ndir1.marked.for.cleanup", listDir(cachesDir))

    Assert.assertTrue(cleanup.runCleanupIfRequired())

    Assert.assertEquals("dir1", listDir(cachesDir))
  }

  @Test
  fun cleanupProcess() {
    val cachesDir = tempDir.createDir()
    val markerFile = cachesDir.resolve(CacheDirCleanup.LAST_CLEANUP_MARKER_FILE_NAME)
    val cleanup = CacheDirCleanup(cachesDir)

    val dir1 = cachesDir.resolve("dir1")
    Files.createDirectories(dir1)
    dir1.resolve("xxx").write("xxx")

    val dir2 = cachesDir.resolve("dir2")
    Files.createDirectories(dir2)
    dir2.resolve("xxx").write("xxx")

    Assert.assertTrue(cleanup.runCleanupIfRequired())

    // Subsequent run should not run at all (no time passed yet, CLEANUP_EVERY_DURATION)
    Assert.assertFalse(cleanup.runCleanupIfRequired())

    // Nothing cleaned-up
    Assert.assertEquals("dir1\ndir2", listDir(cachesDir))

    // Mark directory for deletion, 30 days old
    dir1.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis() - 86400L * 30 * 1000))
    markerFile.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis() - 86400L * 14 * 1000))

    Assert.assertTrue(cleanup.runCleanupIfRequired())
    // dir1 marked for clean-up
    Assert.assertEquals("dir1\ndir1.marked.for.cleanup\ndir2", listDir(cachesDir))

    markerFile.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis() - 86400L * 14 * 1000))
    Assert.assertTrue(cleanup.runCleanupIfRequired())

    // dir1 cleaned-up
    Assert.assertEquals("dir2", listDir(cachesDir))
  }

  private fun listDir(dir: Path): String =
    BuildDependenciesUtil.listDirectory(dir)
      .map { it.name }
      .sorted()
      .filter { it != CacheDirCleanup.LAST_CLEANUP_MARKER_FILE_NAME }
      .joinToString("\n")
}