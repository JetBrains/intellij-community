// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger


@ApiStatus.Internal
class IndexingReasonExplanationLogger {
  companion object {
    private const val MAX_EXPLANATIONS_PER_TYPE: Int = 10
    val LOG: Logger = logger<IndexingReasonExplanationLogger>()
  }

  private val cntIndexingReasonsExplained: AtomicInteger = AtomicInteger()
  private val cntAppliersAndRemoversExplained: AtomicInteger = AtomicInteger()
  private val cntAppliersOnlyExplained: AtomicInteger = AtomicInteger()
  private val cntRemoversOnlyExplained: AtomicInteger = AtomicInteger()

  private fun log(infoSeverity: Boolean, file: IndexedFile, explanation: (IndexedFile) -> String) {
    if (infoSeverity) LOG.info(explanation(file))
    else if (LOG.isTraceEnabled) LOG.trace(explanation(file))
  }

  fun logFileIndexingReason(file: IndexedFile, explanation: (IndexedFile) -> String) {
    log(cntIndexingReasonsExplained.incrementAndGet() <= MAX_EXPLANATIONS_PER_TYPE, file, explanation)
  }

  fun logScannerAppliersAndRemoversForFile(file: IndexedFile, explanation: (IndexedFile) -> String) {
    log(cntAppliersAndRemoversExplained.incrementAndGet() <= MAX_EXPLANATIONS_PER_TYPE, file, explanation)
  }

  fun logScannerAppliersOnlyForFile(file: IndexedFile, explanation: (IndexedFile) -> String) {
    log(cntAppliersOnlyExplained.incrementAndGet() <= MAX_EXPLANATIONS_PER_TYPE, file, explanation)
  }

  fun logScannerRemoversOnlyForFile(file: IndexedFile, explanation: (IndexedFile) -> String) {
    log(cntRemoversOnlyExplained.incrementAndGet() <= MAX_EXPLANATIONS_PER_TYPE, file, explanation)
  }
}