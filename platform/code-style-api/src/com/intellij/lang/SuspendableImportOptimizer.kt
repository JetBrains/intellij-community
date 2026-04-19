// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Extension of [ImportOptimizer] for coroutine-aware implementations.
 *
 * Unlike [ImportOptimizer.processFile], the [processFileSuspend] method:
 * - Is **not** called under a read action
 *
 * Should be registered as `com.intellij.lang.importOptimizer` extension.
 *
 * @see ImportOptimizer
 */
@ApiStatus.Experimental
interface SuspendableImportOptimizer : ImportOptimizer {

  override fun processFile(file: PsiFile): Runnable = EmptyRunnable.getInstance()

  /**
   * Coroutine-aware replacement for [ImportOptimizer.processFile].
   *
   * This method is **not** called under a read action.
   */
  suspend fun processFileSuspend(file: PsiFile): Runnable
}
