// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.collections.visualizer

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.xdebugger.frame.XFullValueEvaluator
import org.jetbrains.annotations.ApiStatus

@Suppress("unused")
@Deprecated("Provide an extension for com.intellij.debugger.collections.visualizer.core.GridBasedCollectionVisualizer instead.")
@ApiStatus.ScheduledForRemoval
object CollectionVisualizerEvaluator {
  @Deprecated("This method always returns null. Provide an extension for com.intellij.debugger.collections.visualizer.core.GridBasedCollectionVisualizer instead.")
  @JvmStatic
  fun createFor(evaluationContext: EvaluationContextImpl, valueDescriptor: ValueDescriptor): XFullValueEvaluator? = null
}
