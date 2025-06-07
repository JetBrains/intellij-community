// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.openapi.util.NlsContexts
import com.intellij.unscramble.DumpItem
import com.intellij.unscramble.MergeableDumpItem
import org.jetbrains.annotations.ApiStatus

/**
 * A provider of [DumpItem] instances, which may be shown in [com.intellij.unscramble.ThreadDumpPanel].
 * This provider may provide any extra items such as coroutines or virtual threads.
 */
@ApiStatus.Internal
abstract class ThreadDumpItemsProviderFactory {
  abstract fun getProvider(context: DebuggerContextImpl): ThreadDumpItemsProvider
}

@ApiStatus.Internal
interface ThreadDumpItemsProvider {
  @get:NlsContexts.ProgressTitle
  val progressText: String

  /**
   * Returns whether this provider requires [SuspendContextImpl] which can be used to evaluate some information to provide dump items.
   */
  val requiresEvaluation: Boolean

  /**
   * Computes a list of dump items optionally using the given suspend context which is ready for evaluation.
   *
   * [suspendContext] is guaranteed to be non-null if [requiresEvaluation] is `true`.
   */
  fun getItems(suspendContext: SuspendContextImpl?): List<MergeableDumpItem>
}