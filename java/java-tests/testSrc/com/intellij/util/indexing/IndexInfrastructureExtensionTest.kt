// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.indexing.FileBasedIndexInfrastructureExtension.InitializationResult
import org.junit.Assert
import java.nio.file.Files
import java.util.SortedMap
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class IndexInfrastructureExtensionTest : LightJavaCodeInsightFixtureTestCase() {
  fun `test infrastructure extension drops all indexes when it requires invalidation`() {
    val text =
      "<fileBasedIndexInfrastructureExtension implementation=\"" + TestIndexInfrastructureExtensionRequiringIndexRebuild::class.java.name + "\"/>"
    Disposer.register(testRootDisposable, loadExtensionWithText(text))

    //[fileName:String -> lastModificationTimestampMs:Long]
    val before = listFilesInIndexFolder()

    val switcher = FileBasedIndexTumbler("IndexInfrastructureExtensionTest")
    switcher.turnOff()
    switcher.turnOn()

    //We need to wait for indexes to re-initialize, and also to fill in with data, since some files will be re-created
    // only on demand:
    val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
    fileBasedIndex.waitUntilIndicesAreInitialized()
    //TODO RC: find better way to wait for indexes to re-initialize
    DumbService.getInstance(project).waitForSmartMode(10_000)

    val after = listFilesInIndexFolder()

    val unmatchedEntries = mismatchBetween(before, after)

    val containsExtensionCaches = after.keys.any { it.endsWith(testInfraExtensionFileSuffix) }

    Assert.assertTrue(
      "On index re-initialization, TestIndexInfrastructureExtensionRequiringIndexRebuild demands all index files to be re-created, " +
      "hence it must be the same files, only with newer timestamps, but: \n$unmatchedEntries",
      unmatchedEntries.isEmpty()
    )
    Assert.assertTrue(
      "On re-initialization, TestIndexInfrastructureExtensionRequiringIndexRebuild must be called, " +
      "and it must create files with [$testInfraExtensionFileSuffix] suffix",
      containsExtensionCaches
    )
  }

  private fun listFilesInIndexFolder(): SortedMap<String, Long> = Files.list(PathManager.getIndexRoot()).use {
    it.toList()
      //we're interested in directories=indexes, and "*testInfraExtensionFileSuffix" files, while other files are out of
      //  interest and only make test unstable:
      .filter { file ->
        (file.isDirectory() && file.name != "fastAttributes" && file.name != "fast_index_stamps")
        || file.name.endsWith(testInfraExtensionFileSuffix)
      }
      .associate { p -> p.fileName.toString() to p.getLastModifiedTime().toMillis() }.toSortedMap()
  }

  private fun mismatchBetween(
    before: SortedMap<String, Long>,
    after: SortedMap<String, Long>,
  ): MutableList<Pair<Map.Entry<String, Long>?, Map.Entry<String, Long>?>> {
    val unmatchedEntries = mutableListOf<Pair<Map.Entry<String, Long>?, Map.Entry<String, Long>?>>()
    for (indexFileBefore in before.entries) {
      val indexFileAfterModificationTimestamp = after[indexFileBefore.key]
      if (indexFileAfterModificationTimestamp == null) {
        unmatchedEntries += (indexFileBefore to null)
      }
      else if (indexFileBefore.value >= indexFileAfterModificationTimestamp) {
        unmatchedEntries += (indexFileBefore to java.util.Map.entry(indexFileBefore.key, indexFileAfterModificationTimestamp))
      }
    }
    for (indexFileAfter in after.entries) {
      val indexFileBeforeModificationTimestamp = after[indexFileAfter.key]
      if (indexFileBeforeModificationTimestamp == null) {
        unmatchedEntries += (null to indexFileAfter)
      }//else: don't need to check timestamps, since they were checked in a first loop
    }
    return unmatchedEntries
  }
}

const val testInfraExtensionFileSuffix: String = "_test_extension"

/**
 * 1. Creates a `..testInfraExtensionFile` file during [combineIndex]
 * 2. Requests index rebuild in [initialize]
 */
@InternalIgnoreDependencyViolation
class TestIndexInfrastructureExtensionRequiringIndexRebuild : FileBasedIndexInfrastructureExtension {
  override fun createFileIndexingStatusProcessor(project: Project): FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor? =
    null

  override fun <K, V> combineIndex(
    indexExtension: FileBasedIndexExtension<K, V>,
    baseIndex: UpdatableIndex<K, V, FileContent, *>,
  ): UpdatableIndex<K, V, FileContent, *>? {
    Files.createFile(PathManager.getIndexRoot().resolve(indexExtension.name.name + testInfraExtensionFileSuffix))
    return null
  }

  override fun initialize(indexLayoutId: String?): InitializationResult = InitializationResult.INDEX_REBUILD_REQUIRED

  override fun onFileBasedIndexVersionChanged(indexId: ID<*, *>) {}

  override fun onStubIndexVersionChanged(indexId: StubIndexKey<*, *>) {}

  override fun attachData(project: Project) {}

  override fun resetPersistentState() {}

  override fun resetPersistentState(indexId: ID<*, *>) {}

  override fun shutdown() {}

  override fun getVersion(): Int = 0

}
