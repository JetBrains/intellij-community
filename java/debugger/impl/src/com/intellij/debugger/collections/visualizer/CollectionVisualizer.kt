// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.collections.visualizer

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

interface CollectionVisualizer {
  fun createComponent(
    project: Project,
    baseCollectionClass: String,
    descriptor: ValueDescriptor,
    evaluationContext: EvaluationContextImpl,
    scope: CoroutineScope,
  ): JComponent?

  companion object {
    private val EP_NAME = ExtensionPointName.create<CollectionVisualizer>("com.intellij.debugger.collectionVisualizer")

    fun createComponent(
      project: Project,
      baseCollectionClass: String,
      valueDescriptor: ValueDescriptor,
      evaluationContext: EvaluationContextImpl,
      scope: CoroutineScope
    ): JComponent? {
      for (visualizer in EP_NAME.extensionList) {
        val component = visualizer.createComponent(project, baseCollectionClass, valueDescriptor, evaluationContext, scope) ?: continue
        return component
      }
      return null
    }
  }
}
