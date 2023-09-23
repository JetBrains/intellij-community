// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.progress.EmptyProgressIndicator
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
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.diagnostic.ProjectScanningHistory
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.mocks.*
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import it.unimi.dsi.fastutil.longs.LongSet
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

    @AfterClass
    @JvmStatic
    fun resetRegisteredIndexes() {
      runInEdtAndWait {
        val tumbler = FileBasedIndexTumbler("test")
        tumbler.turnOff()
        tumbler.turnOn()
      }
    }
  }

  @Rule
  @JvmField
  val tempDir: TemporaryDirectory = TemporaryDirectory()

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
    val indexer = ConfigurableTextFileIndexer(dependsOnContent = true)
    registerIndexers(indexer)

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
  fun `test contentless indexes applied only while scanning`() {
    val indexer = ConfigurableTextFileIndexer(dependsOnContent = false)
    registerIndexers(indexer)

    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing()

    val (_, dirtyFiles) = scanFiles(filesAndDirs)
    assertThat(indexer.getAndResetIndexedFiles())
      .withFailMessage("Contentless indexes are applied during scanning")
      .isNotEmpty()

    indexFiles(filesAndDirs, dirtyFiles)
    captureIndexingResults(indexer).assertNoIndexerIndexedFiles("Contentless indexes are applied during scanning. Avoid double indexing.")
  }

  @Test
  fun `test up-to-date indexes not refreshed (contentless)`() {
    val contentIndexer = ConfigurableTextFileIndexer(dependsOnContent = true)
    val contentlessIndexer = ConfigurableTextFileIndexer(dependsOnContent = false)
    val indexers = listOf(contentIndexer, contentlessIndexer)
    registerIndexers(indexers)

    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing()
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(indexers)
      .assertAllIndexersIndexedFiles()

    // Invalidate content index only
    contentIndexer.additionalInputFilter = { false }
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(indexers)
      .assertNoIndexerIndexedFiles("Indexer should not be invoked to remove previously indexed values")

    // all the text files now dirty because of content indexer. We should refresh only dirty index in this case.
    contentIndexer.additionalInputFilter = { true }
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(indexers)
      .assertOnlySpecificIndexesIndexedFiles("Should refresh only dirty index", contentIndexer)
  }

  @Test
  fun `test scanner does not schedule indexed files for indexing again (change contentlessIndexer_accept return value)`() {
    val indexer = ConfigurableTextFileIndexer(dependsOnContent = false)
    registerIndexers(indexer)

    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing()

    // Index files
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(indexer).assertAllIndexersIndexedFiles()

    // Unindex files. Note that we don't need to increase indexer version. "Accepts" behavior may change, for example, after
    // user changed the project model (e.g., file is no longe in sources dir)
    indexer.additionalInputFilter = { false }
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(indexer).assertNoIndexerIndexedFiles("Indexer should not be invoked to remove previously indexed values")

    // Scan again after "unindexing"
    val (scanningStat, _) = scanFiles(filesAndDirs)
    captureIndexingResults(indexer).assertNoIndexerIndexedFiles("Nothing changed since last indexing. Should detect no files for indexing")
    assertEquals(0, scanningStat.numberOfFilesForIndexing)
  }

  @Test
  fun `test indexes for filetype do not delete data by indexes with no filetype`() {
    // setup is the following: we have files with unique filetype (recognized by unique file extension) and exactly one content indexer
    // associated with the filetype, and at least one contentless indexer not associated with any filetype.
    val filetype = FakeFileType()
    registerFiletype(filetype)

    val indexers = listOf(
      ConfigurableFiletypeSpecificFileIndexer(filetype),
      ConfigurableNoFiletypeFileIndexer(dependsOnContent = true)
    )
    registerIndexers(indexers)

    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing(filetype)

    // We check that file with unique extension is indexed by contentless indexer (during scanning), and then by content indexer
    // (during indexing), and while indexing content, data by contentless indexer is not accidentally erased. This is the first run:
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(indexers).assertAllIndexersIndexedFiles()

    // this is the second run. If data was removed, contentless indexer will be applied during scanning and this will fail the test
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(indexers).assertNoIndexerIndexedFiles()
  }

  @Test
  fun `test indexes for filetype do not delete data by indexes with no filetype (2)`() {
    // setup is the following: we have files with unique filetype (recognized by unique file extension) and no indexers associated with
    // the filetype. Additionally, there is at least one content and at least one contentless indexer willing to index these files.
    val filetype = FakeFileType()
    registerFiletype(filetype)

    val indexers = listOf(
      ConfigurableNoFiletypeFileIndexer(dependsOnContent = true),
      ConfigurableNoFiletypeFileIndexer(dependsOnContent = false)
    )
    registerIndexers(indexers)

    val filesAndDirs = setupSimpleRepresentativeFolderForIndexing(filetype)

    // We check that file with unique extension is indexed by contentless indexer (during scanning), and then by content indexer
    // (during indexing), and while indexing content, data by contentless indexer is not accidentally erased. This is the first run:
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(indexers).assertAllIndexersIndexedFiles()

    // this is the second run. If data was removed, contentless indexer will be applied during scanning and this will fail the test
    scanAndIndexFiles(filesAndDirs)
    captureIndexingResults(indexers).assertNoIndexerIndexedFiles()
  }

  private fun registerFiletype(filetype: FakeFileType) {
    runInEdtAndWait {
      (FileTypeManager.getInstance() as FileTypeManagerImpl).registerFileType(
        filetype, listOf(ExtensionFileNameMatcher(filetype.defaultExtension)),
        testRootDisposable, PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
      )
    }
  }

  private fun captureIndexingResults(indexers: Collection<ConfigurableFileIndexerBase>) = captureIndexingResults(*indexers.toTypedArray())
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

  private fun registerIndexers(indexers: Collection<FileBasedIndexExtension<*, *>>) = registerIndexers(*indexers.toTypedArray())
  private fun registerIndexers(vararg indexers: FileBasedIndexExtension<*, *>) {
    runInEdtAndWait {
      val tumbler = FileBasedIndexTumbler("test")
      tumbler.turnOff()
      indexers.forEach { indexer ->
        application.registerExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, indexer, testRootDisposable)
      }
      tumbler.turnOn()
    }
  }

  private fun scanAndIndexFiles(filesAndDirs: SingleRootIndexableFilesIterator) {
    val (_, dirtyFiles) = scanFiles(filesAndDirs)
    indexFiles(filesAndDirs, dirtyFiles)
  }

  private fun indexFiles(provider: SingleRootIndexableFilesIterator, dirtyFiles: Collection<VirtualFile>) {
    val indexingStamp = project.service<ProjectIndexingDependenciesService>().invalidateAllStamps()
    val indexingTask = UnindexedFilesIndexer(project, mapOf(provider to dirtyFiles), "Test", LongSet.of(), indexingStamp)
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

  private fun scanFiles(filesAndDirs: IndexableFilesIterator): Pair<ProjectScanningHistory, Map<IndexableFilesIterator, Collection<VirtualFile>>> {
    val scanningHistoryRef = Ref<ProjectScanningHistory>()
    val scanningTask = object : UnindexedFilesScanner(project, false, false, listOf(filesAndDirs), null, "Test", ScanningType.PARTIAL,
                                                      project.service<ProjectIndexingDependenciesService>().getLatestIndexingRequestToken()) {
      override fun performScanningAndIndexing(indicator: CheckCancelOnlyProgressIndicator,
                                              progressReporter: IndexingProgressReporter): ProjectScanningHistory {
        return super.performScanningAndIndexing(indicator, progressReporter).also(scanningHistoryRef::set)
      }
    }
    scanningTask.setFlushQueueAfterScanning(false)
    val indicator = EmptyProgressIndicator()
    ProgressManager.getInstance().runProcess({ scanningTask.perform(indicator) }, indicator)

    val history = scanningHistoryRef.get()
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
   *
   * and optionally two files: one in top-level and one in non-top-level directory with default extension of given [filetype]
   */
  private fun setupSimpleRepresentativeFolderForIndexing(filetype: FileType? = null): SingleRootIndexableFilesIterator {
    val dir = tempDir.createDir()
    val testData = getTestDataPath().resolve("simpleRepresentativeFolder")
    FileUtil.copyDir(testData.toFile(), dir.toFile())
    Files.createDirectory(dir.resolve("empty"))
    Files.createDirectories(dir.resolve("dir/empty"))

    filetype?.defaultExtension?.let {
      Files.createFile(dir.resolve("file_top_level.$it"))
      Files.createFile(dir.resolve("dir/file2.$it"))
    }

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