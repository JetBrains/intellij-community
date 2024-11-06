// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render

import com.intellij.debugger.collections.visualizer.CollectionVisualizerEvaluator
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.xdebugger.frame.XFullValueEvaluator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class CollectionReferenceRenderer(
  rendererSettings: NodeRendererSettings,
  name: String,
  labelRenderer: ValueLabelRenderer,
  childrenRenderer: ChildrenRenderer,
) : CompoundReferenceRenderer(rendererSettings,
                              name,
                              labelRenderer,
                              childrenRenderer) {
  override fun getFullValueEvaluator(evaluationContext: EvaluationContextImpl, valueDescriptor: ValueDescriptorImpl): XFullValueEvaluator? {
    return super.getFullValueEvaluator(evaluationContext, valueDescriptor)
           ?: CollectionVisualizerEvaluator.createFor(evaluationContext, valueDescriptor)
  }
}
