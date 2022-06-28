// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.function.Consumer

/**
 * A blocking [Flow][kotlinx.coroutines.flow.Flow] analog of [DocumentationContent] updates, usable from Java.
 *
 * Example usage:
 * ```
 * DocumentationResult.documentation(...).updates(updateConsumer -> {
 *   // do blocking stuff with ProgressManager.checkCanceled()
 *   updateConsumer.consume(DocumentationContent.content(updatedHtml1);
 *   // do more blocking stuff with ProgressManager.checkCanceled()
 *   updateConsumer.consume(DocumentationContent.content(updatedHtml2, updatedImages2);
 * });
 * ```
 */
@Experimental
fun interface DocumentationContentUpdater {

  /**
   * The updates are collected until this function returns, so it is expected to block if needed.
   * After returning from this function an attempt to feed the [updateConsumer] with more updates will result in an exception.
   *
   * To support cancellation [ProgressManager.checkCanceled()][com.intellij.openapi.progress.ProgressManager.checkCanceled]
   * must be called regularly.
   */
  @RequiresReadLockAbsence
  @RequiresBackgroundThread
  fun updateContent(updateConsumer: Consumer<in DocumentationContent>)
}
