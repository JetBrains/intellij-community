// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.junit.Assert
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.readText

@RunWith(Parameterized::class)
internal class BuildDependenciesExtractTest(private val archiveType: TestArchiveType) {
  companion object {
    @Parameterized.Parameters
    @JvmStatic
    fun data(): Collection<Array<Any>> = listOf(arrayOf(TestArchiveType.ZIP), arrayOf(TestArchiveType.TAR_GZ))

    private val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")
  }

  @Suppress("DEPRECATION")
  @Rule
  @JvmField
  val thrown: ExpectedException = ExpectedException.none()

  @Rule
  @JvmField
  val temp = TemporaryFolder()

  @Test
  fun `extractFileToCacheLocation - different options`() {
    val testArchive = createTestFile(archiveType, listOf(TestFile("top-level/test.txt")))

    val root1 = BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)
    Assert.assertEquals("top-level/test.txt", Files.readString(root1.resolve("top-level/test.txt")))

    val root2 = BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive,
      BuildDependenciesExtractOptions.STRIP_ROOT)
    Assert.assertEquals("top-level/test.txt", Files.readString(root2.resolve("test.txt")))

    Assert.assertNotEquals(root1.toString(), root2.toString())

    assertUpToDate {
      val root1_copy = BuildDependenciesDownloader.extractFileToCacheLocation(
        BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)
      Assert.assertEquals(root1.toString(), root1_copy.toString())
    }

    assertUpToDate {
      val root2_copy = BuildDependenciesDownloader.extractFileToCacheLocation(
        BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive, BuildDependenciesExtractOptions.STRIP_ROOT)
      Assert.assertEquals(root2.toString(), root2_copy.toString())
    }
  }

  @Test
  fun `extractFileToCacheLocation - symlinks`() {
    val testArchive = createTestFile(archiveType, listOf(
      TestFile("top-level/test.txt"),
      TestFile("top-level/dir/test.symlink", symlinkTarget = "../test.txt"),
    ))

    val root = BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)

    val symlinkFile = root.resolve("top-level/dir/test.symlink")
    if (SystemInfo.isWindows) {
      Assert.assertTrue(Files.isRegularFile(symlinkFile, LinkOption.NOFOLLOW_LINKS))
      Assert.assertEquals("top-level/test.txt", symlinkFile.readText())
    }
    else {
      val target = Files.readSymbolicLink(symlinkFile)
      Assert.assertEquals("../test.txt", target.toString())
    }
  }

  @Test
  fun `extractFileToCacheLocation - symlinks missing target`() {
    val testArchive = createTestFile(archiveType, listOf(
      TestFile("top-level/dir/test.symlink", symlinkTarget = "test.txt"),
    ))

    val root = BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)

    val symlinkFile = root.resolve("top-level/dir/test.symlink")
    if (SystemInfo.isWindows) {
      Assert.assertFalse(Files.exists(symlinkFile, LinkOption.NOFOLLOW_LINKS))
    }
    else {
      val target = Files.readSymbolicLink(symlinkFile)
      Assert.assertEquals("test.txt", target.toString())
    }
  }

  @Test
  fun `extractFileToCacheLocation - executable bit`() {
    Assume.assumeFalse(isWindows)

    val testArchive = createTestFile(archiveType, listOf(
      TestFile("exec", executable = true),
      TestFile("no-exec", executable = false),
    ))

    val root = BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)

    Assert.assertTrue(Files.getPosixFilePermissions(root.resolve("exec")).contains(PosixFilePermission.OWNER_EXECUTE))
    Assert.assertFalse(Files.getPosixFilePermissions(root.resolve("no-exec")).contains(PosixFilePermission.OWNER_EXECUTE))
  }

  @Test
  fun `extractFileToCacheLocation - symlink pointing to outside location`(): Unit = runBlocking {
    val testArchive = createTestFile(archiveType, listOf(
      TestFile("dir/test.symlink", symlinkTarget = "../dir/.///../../test.txt"),
      TestFile("dir/test.symlink2"),
    ))

    val root = extractFileToCacheLocation(testArchive, BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)

    // will be skipped
    assertThat(root.resolve("dir/test.symlink")).doesNotExist()
    assertThat(root.resolve("dir/test.symlink2")).exists()
  }

  @Test
  fun `extractFileToCacheLocation - symlink pointing to directory`() {
    val testArchive = createTestFile(archiveType, listOf(
      TestFile("dir/test.symlink", symlinkTarget = "sub"),
      TestFile("dir/sub/test.file"),
    ))

    val root = BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)

    val target = root.resolve("dir/test.symlink/test.file")

    if (isWindows) {
      // On Windows directory symlinks are not supported
      assertThat(target).doesNotExist()
    }
    else {
      assertThat(target).exists()
    }
  }

  @Test
  fun `extractFileToCacheLocation - strip root with different leading components`() {
    val testArchive = createTestFile(archiveType, listOf(TestFile("top-level1/test1.txt"), TestFile("top-level2/test2.txt")))

    BuildDependenciesDownloader.extractFileToCacheLocation(BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)

    thrown.expectMessage(object : BaseMatcher<String>() {
      val prefix = "should start with previously found prefix"

      override fun describeTo(description: Description) {
        description.appendText(prefix)
      }

      override fun matches(item: Any?): Boolean {
        val str = (item as String)
        return str.contains(prefix)
      }
    })

    BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive,
      BuildDependenciesExtractOptions.STRIP_ROOT)
  }

  @Test
  fun `extractFileToCacheLocation - up-to-date`() {
    val testArchive = createTestFile(archiveType, listOf(TestFile("a")))

    val root = BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)
    Assert.assertEquals("a", Files.readString(root.resolve("a")))

    assertUpToDate {
      val root2 = BuildDependenciesDownloader.extractFileToCacheLocation(
        BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)
      Assert.assertEquals(root.toString(), root2.toString())
    }

    BuildDependenciesUtil.cleanDirectory(root)

    assertSomethingWasExtracted {
      val root3 = BuildDependenciesDownloader.extractFileToCacheLocation(
        BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory, testArchive)
      Assert.assertEquals(root.toString(), root3.toString())
    }
  }

  @Test
  fun `extractFile - extract again on deleting top level file`() = runBlocking {
    val testArchive = createTestFile(archiveType, listOf(TestFile("a"), TestFile("b")))
    val extractRoot = temp.newFolder().toPath()

    BuildDependenciesDownloader.extractFile(testArchive, extractRoot, BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    Assert.assertEquals("a", Files.readString(extractRoot.resolve("a")))
    Assert.assertEquals("b", Files.readString(extractRoot.resolve("b")))

    assertUpToDate {
      BuildDependenciesDownloader.extractFile(testArchive, extractRoot,
                                              BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    }

    Files.delete(extractRoot.resolve("a"))

    assertSomethingWasExtracted {
      BuildDependenciesDownloader.extractFile(testArchive, extractRoot,
                                              BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    }
  }

  @Test
  fun `extractFile - normalize path`() = runBlocking {
    val testArchive = createTestFile(archiveType, listOf(TestFile("a"), TestFile("b")))
    val extractRoot = temp.newFolder().toPath()

    BuildDependenciesDownloader.extractFile(testArchive, extractRoot, BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    Assert.assertEquals("a", Files.readString(extractRoot.resolve("a")))
    Assert.assertEquals("b", Files.readString(extractRoot.resolve("b")))

    assertUpToDate {
      val otherPresentation = testArchive.parent.resolve(".").resolve(testArchive.fileName)
      Assert.assertNotEquals(testArchive, otherPresentation)
      BuildDependenciesDownloader.extractFile(otherPresentation, extractRoot,
                                              BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    }

    assertUpToDate {
      val otherPresentation2 = testArchive.resolve("..").resolve("..").resolve(testArchive.parent.fileName).resolve(testArchive.fileName)
      Assert.assertNotEquals(testArchive, otherPresentation2)
      BuildDependenciesDownloader.extractFile(otherPresentation2, extractRoot,
                                              BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    }
  }

  @Test
  fun `extractFile - extract again on adding top level file`() = runBlocking {
    val testArchive = createTestFile(archiveType, emptyList())
    val extractRoot = temp.newFolder().toPath()

    BuildDependenciesDownloader.extractFile(testArchive, extractRoot, BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    assertUpToDate {
      BuildDependenciesDownloader.extractFile(testArchive, extractRoot,
                                              BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    }

    Files.writeString(extractRoot.resolve("new"), "xx")

    assertSomethingWasExtracted {
      BuildDependenciesDownloader.extractFile(testArchive, extractRoot,
                                              BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    }
  }

  private inline fun assertUpToDate(block: () -> Unit) {
    val oldValue = BuildDependenciesDownloader.getExtractCount()
    block()
    val newValue = BuildDependenciesDownloader.getExtractCount()
    if (oldValue != newValue) {
      Assert.fail("It was expected that archive extraction will be UP-TO-DATE (noop)")
    }
  }

  private inline fun assertSomethingWasExtracted(block: () -> Unit) {
    val oldValue = BuildDependenciesDownloader.getExtractCount()
    block()
    val newValue = BuildDependenciesDownloader.getExtractCount()
    if (oldValue == newValue) {
      Assert.fail("It was expected that archive extraction will take place")
    }
  }

  private fun createTestFile(type: TestArchiveType, files: List<TestFile>): Path {
    val archiveFile = temp.newFile().also { Files.delete(it.toPath()) }.toPath()

    when (type) {
      TestArchiveType.TAR_GZ -> TarArchiveOutputStream(GzipCompressorOutputStream(Files.newOutputStream(archiveFile))).use { tarStream ->
        val createdDirs = mutableSetOf<Path>()

        for (file in files) {
          val path = Path.of(file.path)
          val parent = path.parent

          if (parent != null && createdDirs.add(parent)) {
            val dirEntry = TarArchiveEntry("$parent/")
            tarStream.putArchiveEntry(dirEntry)
            tarStream.closeArchiveEntry()
          }

          if (file.symlinkTarget != null) {
            val entry = TarArchiveEntry(file.path, TarConstants.LF_SYMLINK)
            entry.linkName = file.symlinkTarget
            tarStream.putArchiveEntry(entry)
            tarStream.closeArchiveEntry()
          }
          else {
            val bytes = file.path.toByteArray()

            val entry = TarArchiveEntry(file.path)
            entry.mode = if (file.executable) "755".toInt(radix = 8) else "644".toInt(radix = 8)
            entry.size = bytes.size.toLong()
            tarStream.putArchiveEntry(entry)
            tarStream.write(bytes)
            tarStream.closeArchiveEntry()
          }
        }
      }

      TestArchiveType.ZIP -> ZipArchiveOutputStream(archiveFile).use { zipStream ->
        val createdDirs = mutableSetOf<Path>()

        for (file in files) {
          val path = Path.of(file.path)
          val parent = path.parent

          if (parent != null && createdDirs.add(parent)) {
            val dirEntry = ZipArchiveEntry("$parent/")
            zipStream.putArchiveEntry(dirEntry)
            zipStream.closeArchiveEntry()
          }

          if (file.symlinkTarget != null) {
            val bytes = file.symlinkTarget.toByteArray()

            val entry = ZipArchiveEntry(file.path)
            entry.unixMode = UnixStat.LINK_FLAG
            entry.size = bytes.size.toLong()
            zipStream.putArchiveEntry(entry)
            zipStream.write(bytes)
            zipStream.closeArchiveEntry()
          }
          else {
            val bytes = file.path.toByteArray()

            val entry = ZipArchiveEntry(file.path)
            val unixMode = if (file.executable) "755".toInt(radix = 8) else "644".toInt(radix = 8)
            entry.unixMode = unixMode
            entry.size = bytes.size.toLong()
            zipStream.putArchiveEntry(entry)
            zipStream.write(bytes)
            zipStream.closeArchiveEntry()
          }
        }
      }
    }.let {  } // exhaustive when

    return archiveFile
  }
}

internal enum class TestArchiveType {
  ZIP,
  TAR_GZ,
}

private data class TestFile(
  @JvmField val path: String,
  @JvmField val symlinkTarget: String? = null,
  @JvmField val executable: Boolean = false,
)