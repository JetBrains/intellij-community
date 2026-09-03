// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.editor.impl.FrozenDocument
import org.jetbrains.annotations.ApiStatus

/**
 * The kind of one document commit, with the data that only this kind needs.
 */
@ApiStatus.Internal
sealed class DocumentCommitKind {

  /**
   * A document commit that runs on the thread of the call. It requires the write intent lock.
   *
   * The IDE often starts this commit while it handles an input event, for example a keystroke. The code then needs the
   * syntax structure of the changed document at once. This kind sends the PSI change events.
   */
  object Synchronous : DocumentCommitKind()

  /**
   * A document commit that runs on a thread of the implementation.
   *
   * The IDE schedules this commit when the document changes. It parses in a non blocking read action, and it applies the
   * result in a background write action. This kind sends the PSI change events.
   */
  object Asynchronous : DocumentCommitKind()

  /**
   * A document commit that runs on the thread of the call and never publishes its result.
   *
   * Only a [com.intellij.psi.util.PsiVersioningService.executeWithTimeline] scope can start this commit. This kind does
   * not send the PSI change events. It only updates the syntax structure of the forked timeline.
   */
  @ApiStatus.Experimental
  class Lightweight(
    /**
     * The state captured in the beginning of lightweight commit.
     * It will be the text that the forked PSI matches after this commit
     */
    val frozen: FrozenDocument,
    /**
     * The number of events captured in the beginning of lightweight commit.
     * Only events between the last [eventWatermark] and this [eventWatermark] will be used for lightweight commit.
     */
    val eventWatermark: Int,
    ) : DocumentCommitKind()

  val isLightweight: Boolean get() = this is Lightweight

  val isAsynchronous: Boolean get() = this is Asynchronous
}
