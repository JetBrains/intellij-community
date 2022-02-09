// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.intellij.util.io.Compressor
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path

@RunWith(Parameterized::class)
class BuildDependenciesExtractTest(private val archiveType: TestArchiveType) {
  companion object {
    @Parameterized.Parameters
    @JvmStatic
    fun data(): Collection<Array<Any>> =
      listOf(arrayOf(TestArchiveType.ZIP), arrayOf(TestArchiveType.TAR_GZ))
  }

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
      BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), testArchive)
    Assert.assertEquals("top-level/test.txt", Files.readString(root1.resolve("top-level/test.txt")))

    val root2 = BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), testArchive,
      BuildDependenciesExtractOptions.STRIP_ROOT)
    Assert.assertEquals("top-level/test.txt", Files.readString(root2.resolve("test.txt")))

    Assert.assertNotEquals(root1.toString(), root2.toString())

    assertUpToDate {
      val root1_copy = BuildDependenciesDownloader.extractFileToCacheLocation(
        BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), testArchive)
      Assert.assertEquals(root1.toString(), root1_copy.toString())
    }

    assertUpToDate {
      val root2_copy = BuildDependenciesDownloader.extractFileToCacheLocation(
        BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), testArchive, BuildDependenciesExtractOptions.STRIP_ROOT)
      Assert.assertEquals(root2.toString(), root2_copy.toString())
    }
  }

  @Test
  fun `extractFileToCacheLocation - strip root with different leading components`() {
    val testArchive = createTestFile(archiveType, listOf(TestFile("top-level1/test1.txt"), TestFile("top-level2/test2.txt")))

    BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), testArchive)

    thrown.expectMessage("$testArchive: entry name 'top-level2' should start with previously found prefix 'top-level1/'")
    BuildDependenciesDownloader.extractFileToCacheLocation(
      BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), testArchive,
      BuildDependenciesExtractOptions.STRIP_ROOT)
  }

  @Test
  fun `extractFileToCacheLocation - up-to-date`() {
    val testArchive = createTestFile(archiveType, listOf(TestFile("a")))

    val root = BuildDependenciesDownloader.extractFileToCacheLocation(BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(),
                                                                      testArchive)
    Assert.assertEquals("a", Files.readString(root.resolve("a")))

    assertUpToDate {
      val root2 = BuildDependenciesDownloader.extractFileToCacheLocation(
        BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), testArchive)
      Assert.assertEquals(root.toString(), root2.toString())
    }

    BuildDependenciesUtil.cleanDirectory(root)

    assertSomethingWasExtracted {
      val root3 = BuildDependenciesDownloader.extractFileToCacheLocation(
        BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), testArchive)
      Assert.assertEquals(root.toString(), root3.toString())
    }
  }

  @Test
  fun `extractFile - extract again on deleting top level file`() {
    val testArchive = createTestFile(archiveType, listOf(TestFile("a"), TestFile("b")))
    val extractRoot = temp.newFolder().toPath()

    BuildDependenciesDownloader.extractFile(testArchive, extractRoot, BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory())
    Assert.assertEquals("a", Files.readString(extractRoot.resolve("a")))
    Assert.assertEquals("b", Files.readString(extractRoot.resolve("b")))

    assertUpToDate {
      BuildDependenciesDownloader.extractFile(testArchive, extractRoot,
                                              BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory())
    }

    Files.delete(extractRoot.resolve("a"))

    assertSomethingWasExtracted {
      BuildDependenciesDownloader.extractFile(testArchive, extractRoot,
                                              BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory())
    }
  }

  @Test
  fun `extractFile - extract again on adding top level file`() {
    val testArchive = createTestFile(archiveType, emptyList())
    val extractRoot = temp.newFolder().toPath()

    BuildDependenciesDownloader.extractFile(testArchive, extractRoot, BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory())
    assertUpToDate {
      BuildDependenciesDownloader.extractFile(testArchive, extractRoot,
                                              BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory())
    }

    Files.writeString(extractRoot.resolve("new"), "xx")

    assertSomethingWasExtracted {
      BuildDependenciesDownloader.extractFile(testArchive, extractRoot,
                                              BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory())
    }
  }

  private fun assertUpToDate(block: () -> Unit) {
    val oldValue = BuildDependenciesDownloader.getExtractCount().get()
    block()
    val newValue = BuildDependenciesDownloader.getExtractCount().get()
    if (oldValue != newValue) {
      Assert.fail("It was expected that archive extraction will be UP-TO-DATE (noop)")
    }
  }

  private fun assertSomethingWasExtracted(block: () -> Unit) {
    val oldValue = BuildDependenciesDownloader.getExtractCount().get()
    block()
    val newValue = BuildDependenciesDownloader.getExtractCount().get()
    if (oldValue == newValue) {
      Assert.fail("It was expected that archive extraction will take place")
    }
  }

  private fun createTestFile(type: TestArchiveType, files: List<TestFile>): Path {
    val archiveFile = temp.newFile().also { Files.delete(it.toPath()) }

    val testFilesRoot = temp.newFolder().toPath()
    for (testFile in files) {
      val path = testFilesRoot.resolve(testFile.path)
      Files.createDirectories(path.parent)
      Files.writeString(path, testFile.path)
    }

    when (type) {
      TestArchiveType.ZIP -> Compressor.Zip(archiveFile)
      TestArchiveType.TAR_GZ -> Compressor.Tar(archiveFile, Compressor.Tar.Compression.GZIP)
    }.use { compressor ->
      compressor.addDirectory(testFilesRoot)
    }

    Assert.assertTrue(Files.exists(archiveFile.toPath()))
    return archiveFile.toPath()
  }

  private data class TestFile(val path: String)
  enum class TestArchiveType {
    ZIP,
    TAR_GZ,
  }
}