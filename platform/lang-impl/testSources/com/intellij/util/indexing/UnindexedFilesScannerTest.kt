// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.application
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.mocks.ConfigurableContentlessTextFileIndexer
import com.intellij.util.indexing.mocks.ConfigurableFileIndexerBase
import com.intellij.util.indexing.mocks.ConfigurableTextFileIndexer
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import java.nio.file.Paths

@RunWith(JUnit4::class)
class UnindexedFilesScannerTest {
  companion object {
    @ClassRule
    @JvmField
    val p: ProjectRule = ProjectRule(true, false, null)
  }

  @Rule
  @JvmField
  val tempDir = TemporaryDirectory()

  private lateinit var project: Project
  private lateinit var testRootDisposable: CheckedDisposable

  @Before
  fun setup() {
    project = p.project
    testRootDisposable = Disposer.newCheckedDisposable("ScanningAndIndexingTest")
  }

  @After
  fun tearDown() {
    runInEdtAndWait {
      Disposer.dispose(testRootDisposable)
    }
  }


  private fun getTestDataPath() = Paths.get(PlatformTestUtil.getCommunityPath(), "platform/lang-impl/testData/indexing")

  @Test
  fun `test new files scheduled for indexing`() {
    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing()
    val regularFiles = getRegularFiles(filesAndDirs)

    val (scanningStat, dirtyFiles) = scanFiles(filesAndDirs)

    assertThat(dirtyFiles).containsExactlyInAnyOrder(*regularFiles.toTypedArray())
    assertEquals(regularFiles.size, scanningStat.numberOfFilesForIndexing)
  }


  @Test
  fun `test scanner does not schedule indexed files for indexing again`() {
    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing()

    scanAndIndexFiles(filesAndDirs)

    val (scanningStat, dirtyFiles) = scanFiles(filesAndDirs)

    assertThat(dirtyFiles).isEmpty()
    assertEquals(0, scanningStat.numberOfFilesForIndexing)
  }

  @Test
  fun `test scanner does not schedule indexed files for indexing again (change contentIndexer_accept return value)`() {
    val indexer = ConfigurableTextFileIndexer()
    registerIndexer(indexer)

    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing()

    // Index files
    scanAndIndexFiles(filesAndDirs)
    assertThat(indexer.getAndResetIndexedFiles()).isNotEmpty()

    // Unindex files. Note that we don't need to increase indexer version. "Accepts" behavior may change, for example, after
    // user changed the project model (e.g., file is no longe in sources dir)
    indexer.additionalInputFilter = { false }
    scanAndIndexFiles(filesAndDirs)
    assertThat(indexer.getAndResetIndexedFiles())
      .withFailMessage("Indexer should not be invoked to remove previously indexed values")
      .isEmpty()

    // Scan again after "unindexing"
    val (scanningStat, dirtyFiles) = scanFiles(filesAndDirs)
    assertThat(dirtyFiles)
      .withFailMessage("Nothing changed since last indexing. Should detect no files for indexing")
      .isEmpty()
    assertEquals(0, scanningStat.numberOfFilesForIndexing)
  }

  @Test
  fun `test contentless indexes applied only while scanning (1)`() {
    val indexer = ConfigurableContentlessTextFileIndexer()
    registerIndexer(indexer)

    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing()

    val (_, dirtyFiles) = scanFiles(filesAndDirs)
    assertThat(indexer.getAndResetIndexedFiles())
      .withFailMessage("Contentless indexes are applied during scanning")
      .isNotEmpty()

    indexFiles(filesAndDirs, dirtyFiles)
    assertThat(indexer.getAndResetIndexedFiles())
      .withFailMessage("Contentless indexes are applied during scanning. Avoid double indexing.")
      .isEmpty()
  }

  @Test
  fun `test up-to-date indexes not refreshed (contentless)`() {
    val contentIndexer = ConfigurableTextFileIndexer()
    val contentlessIndexer = ConfigurableContentlessTextFileIndexer()
    registerIndexer(contentIndexer)
    registerIndexer(contentlessIndexer)

    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing()
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(contentIndexer, contentlessIndexer)
      .assertAllIndexersIndexedFiles()

    // Invalidate content index only
    contentIndexer.additionalInputFilter = { false }
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(contentIndexer, contentlessIndexer)
      .assertNoIndexerIndexedFiles("Indexer should not be invoked to remove previously indexed values")

    // all the text files now dirty because of content indexer. We should refresh only dirty index in this case.
    contentIndexer.additionalInputFilter = { true }
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(contentIndexer, contentlessIndexer)
      .assertOnlySpecificIndexesIndexedFiles("Should refresh only dirty index", contentIndexer)
  }

  private fun captureIndexingResults(vararg indexers: ConfigurableFileIndexerBase): IndexingResults {
    val map = mutableMapOf<ConfigurableFileIndexerBase, List<VirtualFile>>()
    for (indexer in indexers) {
      map[indexer] = indexer.getAndResetIndexedFiles()
    }
    return IndexingResults(map)
  }

  private class IndexingResults(private val dirtyFiles: Map<ConfigurableFileIndexerBase, List<VirtualFile>>) {
    fun assertAllIndexersIndexedFiles(message: String = "") {
      for (indexer in dirtyFiles.keys) {
        assertIndexerIndexedFiles(message, indexer)
      }
    }

    fun assertNoIndexerIndexedFiles(message: String = "") {
      for (indexer in dirtyFiles.keys) {
        assertIndexerIndexedNoFiles(message, indexer)
      }
    }

    fun assertOnlySpecificIndexesIndexedFiles(message: String = "", vararg activeIndexers: ConfigurableFileIndexerBase) {
      for (indexer in dirtyFiles.keys) {
        if (activeIndexers.contains(indexer)) {
          assertIndexerIndexedFiles(message, indexer)
        }
        else {
          assertIndexerIndexedNoFiles(message, indexer)
        }
      }
    }

    fun assertIndexerIndexedFiles(message: String = "", indexer: ConfigurableFileIndexerBase) {
      assertThat(dirtyFiles[indexer]!!)
        .withFailMessage("$message%nIndexed did not index anything: ${indexer}")
        .isNotEmpty
    }

    fun assertIndexerIndexedNoFiles(message: String = "", indexer: ConfigurableFileIndexerBase) {
      assertThat(dirtyFiles[indexer]!!)
        .withFailMessage("$message%nIndexed indexed some files: ${indexer}")
        .isEmpty()
    }

  }

  private fun registerIndexer(indexer: FileBasedIndexExtension<*, *>) {
    runInEdtAndWait {
      val tumbler = FileBasedIndexTumbler("test")
      tumbler.turnOff()
      application.registerExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, indexer, testRootDisposable)
      tumbler.turnOn()
    }
  }

  private fun scanAndIndexFiles(filesAndDirs: SingleRootIndexableFilesIterator) {
    val (_, dirtyFiles) = scanFiles(filesAndDirs)
    indexFiles(filesAndDirs, dirtyFiles)
  }

  private fun indexFiles(provider: SingleRootIndexableFilesIterator, dirtyFiles: Collection<VirtualFile>) {
    val indexingTask = UnindexedFilesIndexer(project, mapOf(provider to dirtyFiles), "Test")
    val indicator = EmptyProgressIndicator()
    ProgressManager.getInstance().runProcess({ indexingTask.perform(indicator) }, indicator)
  }

  private fun getRegularFiles(filesAndDirs: SingleRootIndexableFilesIterator): List<VirtualFile> {
    val regularFiles = mutableListOf<VirtualFile>()
    filesAndDirs.iterateFiles(project, { if (!it.isDirectory) regularFiles.add(it) else true }) { true }
    return regularFiles
  }

  private fun scanFiles(filesAndDirs: SingleRootIndexableFilesIterator): Pair<JsonScanningStatistics, Collection<VirtualFile>> {
    val (history, dirtyFilesPerOrigin) = scanFiles(filesAndDirs as IndexableFilesIterator)

    assertThat(dirtyFilesPerOrigin.size).isLessThanOrEqualTo(1)
    val dirtyFiles = dirtyFilesPerOrigin[filesAndDirs] ?: emptyList<VirtualFile>().also {
      assertThat(dirtyFilesPerOrigin).isEmpty()
    }

    assertEquals(1, history.scanningStatistics.size)
    val scanningStat = history.scanningStatistics[0]

    return Pair(scanningStat, dirtyFiles)
  }

  private fun scanFiles(filesAndDirs: IndexableFilesIterator): Pair<ProjectIndexingHistoryImpl, Map<IndexableFilesIterator, Collection<VirtualFile>>> {
    val indexingHistoryRef = Ref<ProjectIndexingHistoryImpl>()
    val scanningTask = object : UnindexedFilesScanner(project, false, false, listOf(filesAndDirs), null, "Test", ScanningType.PARTIAL) {
      override fun performScanningAndIndexing(indicator: ProgressIndicator): ProjectIndexingHistoryImpl {
        return super.performScanningAndIndexing(indicator).also(indexingHistoryRef::set)
      }
    }
    scanningTask.setFlushQueueAfterScanning(false)
    val indicator = EmptyProgressIndicator()
    ProgressManager.getInstance().runProcess({ scanningTask.perform(indicator) }, indicator)

    val history = indexingHistoryRef.get()
    val dirtyFilesPerOrigin = project.service<PerProjectIndexingQueue>().getFilesAndClear()
    return Pair(history, dirtyFilesPerOrigin)
  }

  /**
   * minimalistic representative project that contains at least one:
   *   * txt file in top-level directory
   *   * non-txt file in top-level directory
   *   * empty directory
   *   * non-empty directory
   *   * txt file in non-top-level directory
   *   * non-txt file in non-top-level directory
   */
  private fun setupSimpleRepresentativeFolderForIndexing(): SingleRootIndexableFilesIterator {
    val dir = tempDir.createDir()
    val testData = getTestDataPath().resolve("simpleRepresentativeFolder")
    FileUtil.copyDir(testData.toFile(), dir.toFile())
    Files.createDirectory(dir.resolve("empty"))
    Files.createDirectories(dir.resolve("dir/empty"))

    return SingleRootIndexableFilesIterator(dir.toUri().toString())
  }

  private class FakeOrigin : IndexableSetOrigin

  private class SingleRootIndexableFilesIterator(private val url: String) : IndexableFilesIterator {
    private val origin = FakeOrigin()
    override fun getDebugName(): String = url
    override fun getIndexingProgressText(): String = "Indexing $url"
    override fun getRootsScanningProgressText(): String = "Scanning $url"
    override fun getOrigin(): IndexableSetOrigin = origin
    override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
      val vfile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(url) ?: throw IllegalStateException(
        "vFile not found for URL $url")
      return VfsUtilCore.iterateChildrenRecursively(vfile, { true }, fileIterator)
    }

    override fun getRootUrls(project: Project): MutableSet<String> = mutableSetOf(url)
  }
}