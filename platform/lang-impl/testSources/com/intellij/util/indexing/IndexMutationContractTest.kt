// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.registerExtension
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Verifies that demand-driven index updates preserve exact data while project cursors skip already processed work. */
@TestApplication
internal class IndexMutationContractTest {
  companion object {
    private val indexPackFixture = testFixture("indexing contract index pack") {
      val disposable = Disposer.newDisposable("IndexMutationContractTest index pack")
      val pack = TestIndexPack()
      withContext(Dispatchers.EDT) {
        val tumbler = FileBasedIndexTumbler("IndexMutationContractTest register indexes")
        tumbler.turnOff()
        try {
          val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerImpl
          val corePlugin = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
          listOf(pack.indexedFileType, pack.excludedFileType).forEach { fileType ->
            fileTypeManager.registerFileType(
              fileType,
              listOf(ExtensionFileNameMatcher(fileType.defaultExtension)),
              disposable,
              corePlugin,
            )
          }
          pack.extensions.forEach { extension ->
            application.registerExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, extension, disposable)
          }
        }
        finally {
          tumbler.turnOn()
        }
      }
      initialized(pack) {
        withContext(Dispatchers.EDT) {
          val tumbler = FileBasedIndexTumbler("IndexMutationContractTest unregister indexes")
          tumbler.turnOff()
          try {
            Disposer.dispose(disposable)
          }
          finally {
            tumbler.turnOn()
          }
        }
      }
    }
  }

  private val projectFixture = projectFixture()
  private val moduleFixture = projectFixture.moduleFixture("indexing-contract")
  private val sourceRootFixture = moduleFixture.sourceRootFixture()

  private val indexes get() = indexPackFixture.get()
  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile
  private val projectScope get() = GlobalSearchScope.projectScope(project)

  /** Checks that cursor advancement never leaves stale forward or inverted data after change and deletion. */
  @Test
  @Timeout(30)
  fun `content change and delete replace keys without stale index data`(): Unit = timeoutRunBlocking {
    indexes
    awaitIndexesReady()
    val file = createTextFile("mutable.contract", "one two")

    queryAndEnsureUpToDate(indexes.tokenIndex)
    assertKeys(file, indexes.tokenIndex, "one", "two")
    assertKeys(file, indexes.metadataIndex, "extension:contract", "name:mutable")
    assertFilename("mutable.contract", file, expectedPresent = true)

    val changeCheckpoint = indexes.trace.checkpoint()
    edtWriteAction { file.setBinaryContent("three".toByteArray()) }
    queryAndEnsureUpToDate(indexes.tokenIndex)

    assertKeys(file, indexes.tokenIndex, "three")
    assertKeys(file, indexes.metadataIndex, "extension:contract", "name:mutable")
    assertEquals(1, successfulInvocationCountSince(indexes.tokenIndex, file, changeCheckpoint), "Content mapper must run once")
    assertEquals(1, successfulInvocationCountSince(indexes.metadataIndex, file, changeCheckpoint), "Contentless mapper must run once")

    val staleKeys = setOf("three", "extension:contract", "name:mutable")
    edtWriteAction { file.delete(this) }
    queryAndEnsureUpToDate(indexes.tokenIndex)

    assertRawFileDataEmpty(file, indexes.tokenIndex)
    assertRawFileDataEmpty(file, indexes.metadataIndex)
    staleKeys.forEach { key -> assertKeyHasNoFile(key, file) }
    assertFilename("mutable.contract", file, expectedPresent = false)
  }

  /** Checks that rename refreshes metadata indexes without corrupting content-derived keys. */
  @Test
  @Timeout(30)
  fun `rename updates metadata and filename while preserving content keys`(): Unit = timeoutRunBlocking {
    indexes
    awaitIndexesReady()
    val file = createTextFile("before.contract", "same content")
    queryAndEnsureUpToDate(indexes.tokenIndex)
    val renameCheckpoint = indexes.trace.checkpoint()

    edtWriteAction { file.rename(this, "after.contract") }
    queryAndEnsureUpToDate(indexes.tokenIndex)

    assertKeys(file, indexes.tokenIndex, "content", "same")
    assertKeys(file, indexes.metadataIndex, "extension:contract", "name:after")
    assertKeyFiles(indexes.metadataIndex, "name:before", emptySet())
    assertFilename("before.contract", file, expectedPresent = false)
    assertFilename("after.contract", file, expectedPresent = true)
    assertEquals(
      setOf(setOf("extension:contract", "name:after")),
      successfulInvocationEventsSince(indexes.metadataIndex, file, renameCheckpoint)
        .mapTo(mutableSetOf(), IndexInvocationEvent::producedKeys),
      "Rename metadata mapper must produce only the final file state",
    )
  }

  /** Checks that changing file type removes ineligible content data and keeps eligible metadata current. */
  @Test
  @Timeout(30)
  fun `file type change removes content keys and updates metadata`(): Unit = timeoutRunBlocking {
    indexes
    awaitIndexesReady()
    val file = createTextFile("typed.contract", "eligible token")
    queryAndEnsureUpToDate(indexes.tokenIndex)
    val renameCheckpoint = indexes.trace.checkpoint()

    edtWriteAction { file.rename(this, "typed.contract-excluded") }
    queryAndEnsureUpToDate(indexes.tokenIndex)

    assertKeys(file, indexes.tokenIndex)
    assertKeys(file, indexes.metadataIndex, "extension:contract-excluded", "name:typed")
    assertKeyFiles(indexes.tokenIndex, "eligible", emptySet())
    assertKeyFiles(indexes.tokenIndex, "token", emptySet())
    assertFilename("typed.contract", file, expectedPresent = false)
    assertFilename("typed.contract-excluded", file, expectedPresent = true)
    assertEquals(
      0,
      successfulInvocationCountSince(indexes.tokenIndex, file, renameCheckpoint),
      "Ineligible files must not invoke token mapper",
    )
    assertEquals(
      setOf(setOf("extension:contract-excluded", "name:typed")),
      successfulInvocationEventsSince(indexes.metadataIndex, file, renameCheckpoint)
        .mapTo(mutableSetOf(), IndexInvocationEvent::producedKeys),
      "File type change metadata mapper must produce only the final file state",
    )
  }

  /** Checks that coalesced requests index only the final VFS state and leave no keys from intermediate states. */
  @Test
  @Timeout(30)
  fun `events coalesced before consume produce only final keys`(): Unit = timeoutRunBlocking {
    indexes
    awaitIndexesReady()
    val scannerExecutor = UnindexedFilesScannerExecutorImpl.getInstance(project)
    scannerExecutor.suspendQueue()
    try {
      var file = createTextFile("sequence.contract", "first")
      edtWriteAction { file.setBinaryContent("second".toByteArray()) }
      edtWriteAction { file.rename(this, "renamed.contract") }
      edtWriteAction { file.setBinaryContent("obsolete".toByteArray()) }
      edtWriteAction { file.delete(this) }
      file = createTextFile("renamed.contract", "final state")
      val checkpoint = indexes.trace.checkpoint()

      queryAndEnsureUpToDate(indexes.tokenIndex)

      assertKeys(file, indexes.tokenIndex, "final", "state")
      assertKeys(file, indexes.metadataIndex, "extension:contract", "name:renamed")
      assertKeyFiles(indexes.tokenIndex, "first", emptySet())
      assertKeyFiles(indexes.tokenIndex, "second", emptySet())
      assertKeyFiles(indexes.tokenIndex, "obsolete", emptySet())
      assertFilename("sequence.contract", file, expectedPresent = false)
      assertFilename("renamed.contract", file, expectedPresent = true)
      assertEquals(1, successfulInvocationCountSince(indexes.tokenIndex, file, checkpoint), "Final file must be mapped once")
    }
    finally {
      scannerExecutor.resumeQueue()
    }
  }

  /** Checks that repeated current-index queries produce no mapper workload after the project cursor catches up. */
  @Test
  @Timeout(30)
  fun `repeated query of current indexes performs no mapper work`(): Unit = timeoutRunBlocking {
    indexes
    awaitIndexesReady()
    val file = createTextFile("stable.contract", "stable value")
    queryAndEnsureUpToDate(indexes.tokenIndex)
    assertKeys(file, indexes.tokenIndex, "stable", "value")
    val checkpoint = indexes.trace.checkpoint()

    repeat(2) {
      queryAndEnsureUpToDate(indexes.tokenIndex)
      assertKeys(file, indexes.tokenIndex, "stable", "value")
      assertKeys(file, indexes.metadataIndex, "extension:contract", "name:stable")
    }

    assertNoSuccessfulMapperWorkSince(checkpoint)
  }

  /** Waits for the complete scanner lifecycle before a scenario mutates project content. */
  private suspend fun awaitIndexesReady() {
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
  }

  /** Makes demand-driven processing explicit before assertions inspect public index data. */
  private suspend fun queryAndEnsureUpToDate(index: ContractScalarIndex) {
    readAction {
      FileBasedIndex.getInstance().ensureUpToDate(index.name, project, projectScope)
    }
  }

  /** Creates a VFS file without making indexing synchronization an implicit mutation side effect. */
  private suspend fun createTextFile(name: String, text: String): VirtualFile = edtWriteAction {
    sourceRoot.createChildData(this, name).also { it.setBinaryContent(text.toByteArray()) }
  }

  /** Checks the exact forward keys for one file, including absence of stale keys after mutations. */
  private suspend fun assertKeys(file: VirtualFile, index: ContractScalarIndex, vararg expectedKeys: String) {
    val actual = readAction { FileBasedIndex.getInstance().getFileData(index.name, file, project).keys }
    assertEquals(expectedKeys.toSet(), actual, "${index.kind} keys must match current state of ${file.path}")
  }

  /** Checks exact project-visible files for one key through the public inverted-index API. */
  private suspend fun assertKeyFiles(index: ContractScalarIndex, key: String, expectedNames: Set<String>) {
    val actual = readAction {
      FileBasedIndex.getInstance().getContainingFiles(index.name, key, projectScope).mapTo(mutableSetOf(), VirtualFile::getName)
    }
    assertEquals(expectedNames, actual, "Files for ${index.kind} key '$key' must match project scope")
  }

  /** Confirms deletion removed forward data rather than merely hiding an invalid file through scope filtering. */
  private suspend fun assertRawFileDataEmpty(file: VirtualFile, index: ContractScalarIndex) {
    val actual = readAction { FileBasedIndex.getInstance().getFileData(index.name, file, project) }
    assertEquals(emptyMap<String, Void>(), actual, "Deleted file ${file.path} must have no ${index.kind} forward data")
  }

  /** Confirms an old key cannot still resolve the deleted file through either test index. */
  private suspend fun assertKeyHasNoFile(key: String, file: VirtualFile) {
    listOf(indexes.tokenIndex, indexes.metadataIndex).forEach { index ->
      val files = readAction { FileBasedIndex.getInstance().getContainingFiles(index.name, key, projectScope) }
      assertFalse(files.contains(file), "Stale ${index.kind} key '$key' must not resolve deleted file ${file.path}")
    }
  }

  /** Confirms the production filename index agrees with rename and deletion mutations. */
  private suspend fun assertFilename(name: String, file: VirtualFile, expectedPresent: Boolean) {
    val actual = readAction { FilenameIndex.getVirtualFilesByName(name, projectScope).contains(file) }
    assertEquals(expectedPresent, actual, "FilenameIndex visibility for $name must match the mutation")
  }

  /** Counts completed mapper invocations for one index and file after a scenario checkpoint. */
  private fun successfulInvocationCountSince(index: ContractScalarIndex, file: VirtualFile, checkpoint: Long): Int {
    return successfulInvocationEventsSince(index, file, checkpoint).size
  }

  /** Returns completed mapper events for one index and file after the scenario checkpoint. */
  private fun successfulInvocationEventsSince(
    index: ContractScalarIndex,
    file: VirtualFile,
    checkpoint: Long,
  ): List<IndexInvocationEvent> {
    val fileId = FileBasedIndex.getFileId(file)
    return indexes.trace.eventsSince(checkpoint).filter {
      it.context.index == index.kind && it.context.fileId == fileId && it.outcome == IndexInvocationOutcome.SUCCEEDED
    }
  }

  /** Confirms an up-to-date query did not invoke either mapper after the checkpoint. */
  private fun assertNoSuccessfulMapperWorkSince(checkpoint: Long) {
    val successfulEvents = indexes.trace.eventsSince(checkpoint).filter { it.outcome == IndexInvocationOutcome.SUCCEEDED }
    assertEquals(emptyList<IndexInvocationEvent>(), successfulEvents, "Current index queries must not invoke mappers")
  }
}
