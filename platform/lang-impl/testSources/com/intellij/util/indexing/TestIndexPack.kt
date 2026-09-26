// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Icon

/** Supplies stable content and metadata indexes with an immutable mapper trace for mutation contract tests. */
internal class TestIndexPack {
  val indexedFileType = ContractFileType("IndexingContractText", "contract")
  val excludedFileType = ContractFileType("IndexingContractExcluded", "contract-excluded")
  val trace = IndexInvocationTrace()

  val tokenIndex = ContractScalarIndex(
    kind = TestIndexKind.TOKEN,
    indexId = TOKEN_INDEX_ID,
    dependsOnContent = true,
    inputFilter = FileBasedIndex.InputFilter { it.fileType == indexedFileType },
    trace = trace,
  ) { content ->
    content.contentAsText
      .split(Regex("\\s+"))
      .asSequence()
      .filter(String::isNotBlank)
      .map(String::lowercase)
      .toSet()
  }

  val metadataIndex = ContractScalarIndex(
    kind = TestIndexKind.METADATA,
    indexId = METADATA_INDEX_ID,
    dependsOnContent = false,
    inputFilter = FileBasedIndex.InputFilter { it.fileType == indexedFileType || it.fileType == excludedFileType },
    trace = trace,
  ) { content ->
    val file = content.file
    setOf(
      "extension:${file.extension.orEmpty()}",
      "name:${file.nameWithoutExtension.lowercase()}",
    )
  }

  val extensions: List<FileBasedIndexExtension<*, *>> = listOf(tokenIndex, metadataIndex)

  companion object {
    private val TOKEN_INDEX_ID: ID<String, Void> = ID.create("indexing.contract.test.tokens")
    private val METADATA_INDEX_ID: ID<String, Void> = ID.create("indexing.contract.test.metadata")
  }
}

/** Distinguishes mapper traces without relying on generated extension class or ID names. */
internal enum class TestIndexKind {
  TOKEN,
  METADATA,
}

/** Captures the stable identity and attempt number of one mapper invocation. */
internal data class IndexInvocationContext(
  val index: TestIndexKind,
  val fileId: Int,
  val attempt: Int,
)

/** Distinguishes entry, successful completion, and exceptional completion of one mapper attempt. */
internal enum class IndexInvocationOutcome {
  STARTED,
  SUCCEEDED,
  FAILED,
}

/** Immutable mapper event retained even if the underlying file is renamed or deleted later. */
internal data class IndexInvocationEvent(
  val sequence: Long,
  val context: IndexInvocationContext,
  val outcome: IndexInvocationOutcome,
  val producedKeys: Set<String>,
  val failureClass: String?,
  val threadName: String,
  val readAccessAllowed: Boolean,
  val writeAccessAllowed: Boolean,
)

/** Records immutable mapper events and per-file attempt numbers for exact workload assertions. */
internal class IndexInvocationTrace {
  private val sequence = AtomicLong()
  private val attempts = ConcurrentHashMap<Pair<TestIndexKind, Int>, AtomicInteger>()
  private val events = ConcurrentLinkedQueue<IndexInvocationEvent>()

  /** Returns a marker that can later select only events caused by a scenario action. */
  fun checkpoint(): Long = sequence.get()

  /** Returns a stable snapshot of events recorded strictly after [checkpoint]. */
  fun eventsSince(checkpoint: Long): List<IndexInvocationEvent> = events.filter { it.sequence > checkpoint }

  /** Starts a new numbered attempt before the mapper computes its keys. */
  fun started(index: TestIndexKind, file: VirtualFile): IndexInvocationContext {
    val fileId = (file as VirtualFileWithId).id
    val attempt = attempts.computeIfAbsent(index to fileId) { AtomicInteger() }.incrementAndGet()
    val context = IndexInvocationContext(index, fileId, attempt)
    record(context, IndexInvocationOutcome.STARTED)
    return context
  }

  /** Records successful completion and the exact keys produced by the mapper. */
  fun succeeded(context: IndexInvocationContext, keys: Set<String>) {
    record(context, IndexInvocationOutcome.SUCCEEDED, producedKeys = keys.toSet())
  }

  /** Records exceptional completion without retaining the throwable or its object graph. */
  fun failed(context: IndexInvocationContext, failure: Throwable) {
    record(context, IndexInvocationOutcome.FAILED, failureClass = failure.javaClass.name)
  }

  private fun record(
    context: IndexInvocationContext,
    outcome: IndexInvocationOutcome,
    producedKeys: Set<String> = emptySet(),
    failureClass: String? = null,
  ) {
    val application = ApplicationManager.getApplication()
    events.add(
      IndexInvocationEvent(
        sequence = sequence.incrementAndGet(),
        context = context,
        outcome = outcome,
        producedKeys = producedKeys,
        failureClass = failureClass,
        threadName = Thread.currentThread().name,
        readAccessAllowed = application.isReadAccessAllowed,
        writeAccessAllowed = application.isWriteAccessAllowed,
      )
    )
  }
}

/** File type pair used to verify index eligibility changes caused by rename. */
internal class ContractFileType(private val typeName: String, private val extension: String) : FileType {
  override fun getName(): String = typeName
  override fun getDescription(): String = "$typeName test file"
  override fun getDefaultExtension(): String = extension
  override fun getIcon(): Icon? = null
  override fun isBinary(): Boolean = false
}

/** Maps one file to multiple scalar keys while recording the mapper lifecycle. */
internal class ContractScalarIndex(
  val kind: TestIndexKind,
  private val indexId: ID<String, Void>,
  private val dependsOnContent: Boolean,
  private val inputFilter: FileBasedIndex.InputFilter,
  private val trace: IndexInvocationTrace,
  private val keys: (FileContent) -> Set<String>,
) : ScalarIndexExtension<String>() {
  override fun getName(): ID<String, Void> = indexId
  override fun getVersion(): Int = 1
  override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
  override fun getInputFilter(): FileBasedIndex.InputFilter = inputFilter
  override fun dependsOnFileContent(): Boolean = dependsOnContent
  override fun traceKeyHashToVirtualFileMapping(): Boolean = true

  override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
    val context = trace.started(kind, content.file)
    try {
      val producedKeys = keys(content)
      trace.succeeded(context, producedKeys)
      producedKeys.associateWith { null }
    }
    catch (failure: Throwable) {
      trace.failed(context, failure)
      throw failure
    }
  }
}
