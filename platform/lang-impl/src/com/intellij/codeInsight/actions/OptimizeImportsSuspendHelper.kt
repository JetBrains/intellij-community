// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.lang.SuspendableImportOptimizer
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.PsiFile

internal object OptimizeImportsSuspendHelper {
  /**
   * Finds [SuspendableImportOptimizer] instances that support [file], runs them via coroutines,
   * and returns the collected notification results.
   *
   * Returns an empty list if no suspend optimizers apply to the file.
   * Must be called on a background thread outside of a write action.
   */
  @JvmStatic
  fun collectSuspendOptimizers(file: PsiFile): List<Runnable> {
    val optimizers = runReadActionBlocking {
      val service = FormattingServiceUtil.findImportsOptimizingService(file)
      val allFiles = file.viewProvider.allFiles
      service.getImportOptimizers(file)
        .filterIsInstance<SuspendableImportOptimizer>()
        .filter { opt -> allFiles.any { opt.supports(it) } }
    }
    if (optimizers.isEmpty()) return emptyList()

    return runBlockingCancellable {
      optimizers.map { optimizer -> optimizer.processFileSuspend(file)}
    }
  }
}
