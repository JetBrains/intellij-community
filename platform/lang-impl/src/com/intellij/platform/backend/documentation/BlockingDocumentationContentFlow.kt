// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.util.function.Consumer

/**
 * A blocking [Flow][kotlinx.coroutines.flow.Flow] analog of [DocumentationContent] [updates][DocumentationResult.Data.updates],
 * usable from Java.
 *
 * Example usage:
 * ```
 * DocumentationResult.documentation(...).updates(updateConsumer -> {
 *   // do blocking stuff with ProgressManager.checkCanceled()
 *   updateConsumer.consume(DocumentationContent.content(updatedHtml1);
 *   // do more blocking stuff with ProgressManager.checkCanceled()
 *   updateConsumer.consume(DocumentationContent.content(updatedHtml2, updatedImages2);
 *   // after returning from the `DocumentationContentUpdater.updateContent` the consumer is marked as closed,
 *   // and it's not possible to invoke the consumer anymore
 * });
 * ```
 */
@Experimental
@OverrideOnly
fun interface BlockingDocumentationContentFlow {

  /**
   * The updates are collected until this function returns, so it is expected to block if needed.
   * After returning from this function an attempt to feed the [consumer] with more updates will result in an exception.
   *
   * To support cancellation [ProgressManager.checkCanceled()][com.intellij.openapi.progress.ProgressManager.checkCanceled]
   * must be called regularly.
   */
  @RequiresReadLockAbsence
  @RequiresBackgroundThread
  fun collectBlocking(consumer: Consumer<in DocumentationContent>)
}
