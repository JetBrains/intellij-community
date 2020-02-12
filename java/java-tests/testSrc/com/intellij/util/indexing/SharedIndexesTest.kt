// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.Processor
import com.intellij.util.indexing.hash.FileContentHashIndex
import com.intellij.util.indexing.hash.FileContentHashIndexExtension
import com.intellij.util.indexing.hash.HashBasedMapReduceIndex
import com.intellij.util.indexing.hash.SharedIndexChunkConfiguration
import com.intellij.util.indexing.hash.building.IndexChunk
import com.intellij.util.indexing.hash.building.IndexesExporter
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.UpdatableValueContainer
import com.intellij.util.indexing.impl.ValueContainerImpl
import com.intellij.util.indexing.provided.OnDiskSharedIndexChunkLocator
import com.intellij.util.indexing.provided.SharedIndexChunkLocator
import com.intellij.util.indexing.snapshot.SnapshotSingleValueIndexStorage
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import java.nio.file.Path
import java.util.*
import kotlin.collections.HashMap

// please contact with Dmitry Batkovich in case of any problems
@SkipSlowTestLocally
class SharedIndexesTest : LightJavaCodeInsightFixtureTestCase() {
  private var disposable: Disposable? = null

  override fun setUp() {
    TestApplicationManager.getInstance()
    if (getTestName(false) == "LocatorNotQueriedTooOften") {
      disposable = Disposer.newDisposable()
      SharedIndexChunkLocator.EP_NAME.getPoint(null).registerExtension(DummySharedIndexLocator(), disposable!!)
    }
    super.setUp()
  }

  override fun tearDown() {
    if (disposable != null) {
      Disposer.dispose(disposable!!)
      disposable = null
    }
    super.tearDown()
  }

  private val indexZip: Path by lazy {
    val tempDir = FileUtil.createTempDirectory("shared-indexes-test", "").toPath()
    tempDir.resolve("index.zip")
  }

  fun testLocatorNotQueriedTooOften() {
    DumbService.getInstance(project).queueTask(UnindexedFilesUpdater(project))
    val initial = DummySharedIndexLocator.ourRequestCount.get(project)
    DumbService.getInstance(project).queueTask(UnindexedFilesUpdater(project))
    assertEquals(initial, DummySharedIndexLocator.ourRequestCount.get(project))
    WriteAction.run<Exception> {
      ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true)
    }
    assertEquals(initial!!.inc(), DummySharedIndexLocator.ourRequestCount.get(project) as Int)
  }

  fun testSharedFileContentHashIndex() {
    val internalHashId = 123
    val indexId = 456
    val inputId1 = 100
    val inputId2 = inputId1 + 1

    val extension = FileContentHashIndexExtension(object : SharedIndexChunkConfiguration {
      override fun tryEnumerateContentHash(hash: ByteArray?): Long {

        return FileContentHashIndexExtension.getHashId(internalHashId, indexId)
      }

      override fun locateIndexes(project: Project, entries: MutableSet<OrderEntry>, indicator: ProgressIndicator) {
        throw AssertionFailedError()
      }

      override fun <Value : Any?, Key : Any?> getChunk(indexId: ID<Key, Value>, chunkId: Int): HashBasedMapReduceIndex<Key, Value>? {
        throw AssertionFailedError()
      }

      override fun <Value : Any?, Key : Any?> processChunks(indexId: ID<Key, Value>,
                                                            processor: Processor<UpdatableIndex<Key, Value, FileContent>>) {
        throw AssertionFailedError()
      }

      override fun disposeIndexChunkData(indexId: ID<*, *>, chunkId: Int) {
        throw AssertionFailedError()
      }
    })
    val index = FileContentHashIndex(extension, object : IndexStorage<Long?, Void?> {
      val map: MutableMap<Long, UpdatableValueContainer<Void?>> = HashMap()

      override fun clear() = map.clear()

      override fun clearCaches() = Unit

      override fun removeAllValues(key: Long, inputId: Int) {
        map[key]!!.removeAssociatedValue(inputId)
      }

      override fun flush() = Unit

      override fun addValue(key: Long?, inputId: Int, value: Void?) {
        map.computeIfAbsent(key!!) { ValueContainerImpl() }.addValue(inputId, value)
      }

      override fun close() {}

      override fun read(key: Long?): ValueContainer<Void?> {
        return map[key] ?: SnapshotSingleValueIndexStorage.empty()
      }
    })

    val virtualFile1 = myFixture.addFileToProject("A.java", "").virtualFile
    val virtualFile2 = myFixture.addFileToProject("B.java", "").virtualFile
    index.update(inputId1, FileContentImpl.createByFile(virtualFile1)).compute()
    index.update(inputId2, FileContentImpl.createByFile(virtualFile2)).compute()

    assertEquals(internalHashId, FileContentHashIndexExtension.getInternalHashId(index.getHashId(inputId1)))
    assertEquals(internalHashId, FileContentHashIndexExtension.getInternalHashId(index.getHashId(inputId2)))
    assertEquals(indexId, FileContentHashIndexExtension.getIndexId(index.getHashId(inputId1)))
    assertEquals(indexId, FileContentHashIndexExtension.getIndexId(index.getHashId(inputId2)))

    // order matters
    val expected = intArrayOf(inputId1, inputId2)
    val actual = index.getHashIdToFileIdsFunction(indexId).remap(internalHashId)
    assertTrue(Arrays.equals(expected, actual))
    index.dispose()
  }

  fun testSharedIndexesForProject() {
    try {
      val virtualFile = myFixture.configureByText("A.java", """
      public class A { 
        public void foo() {
          //Comment
        }
      }
    """.trimIndent()).virtualFile

      val chunks = arrayListOf<IndexChunk>()
      chunks += IndexChunk(setOf(virtualFile), "test-shared-index.ijx")

      IndexesExporter
        .getInstance(project)
        .exportIndices(chunks, indexZip, EmptyProgressIndicator())

      restartFileBasedIndex(indexZip, project)

      val fileBasedIndex = FileBasedIndex.getInstance()

      val map = fileBasedIndex.getFileData(StubUpdatingIndex.INDEX_ID, virtualFile, project)
      TestCase.assertTrue(map.isNotEmpty())

      val values = fileBasedIndex.getValues(IdIndex.NAME, IdIndexEntry("Comment", true), GlobalSearchScope.allScope(project))
      TestCase.assertEquals(UsageSearchContext.IN_COMMENTS.toInt(), values.single())
      assertHashIndexContainsProperData((virtualFile as VirtualFileWithId).id)
    } finally {
      restartFileBasedIndex(null, project)
    }
  }

  private fun assertHashIndexContainsProperData(fileId: Int) {
    val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val hashId = index.getOrCreateFileContentHashIndex().getHashId(fileId)
    assertTrue(hashId != FileContentHashIndexExtension.NULL_HASH_ID)
  }

  companion object {
    @JvmStatic
    fun restartFileBasedIndex(indexZip: Path?, project: Project?) {
      val indexSwitcher = FileBasedIndexSwitcher(FileBasedIndex.getInstance() as FileBasedIndexImpl)
      ApplicationManager.getApplication().runWriteAction {
        indexSwitcher.turnOff()
      }

      FileUtil.delete(PathManager.getIndexRoot())
      val rootProp = indexZip?.toAbsolutePath()?.toString()
      if (rootProp == null) {
        System.clearProperty(OnDiskSharedIndexChunkLocator.ROOT_PROP)
      } else {
        System.setProperty(OnDiskSharedIndexChunkLocator.ROOT_PROP, rootProp)
      }

      ApplicationManager.getApplication().runWriteAction {
        IndexingStamp.flushCaches()
        if (project != null) {
          FileBasedIndex.getInstance().iterateIndexableFilesConcurrently({
                                                                           dropIndexingStampsRecursively(it)
                                                                           true
                                                                         }, project, EmptyProgressIndicator())
          ProjectRootManagerEx.getInstanceEx(project).incModificationCount()
        }
        indexSwitcher.turnOn()
      }
    }

    @JvmStatic
    fun dropIndexingStampsRecursively(file: VirtualFile) {
      file as VirtualFileSystemEntry
      file.isFileIndexed = false;
      IndexingStamp.dropIndexingTimeStamps(file.id)
    }
  }
}