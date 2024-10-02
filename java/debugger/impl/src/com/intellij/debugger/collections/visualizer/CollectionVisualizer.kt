// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.collections.visualizer

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

interface CollectionVisualizer {
  fun applicableFor(collectionClass: String): Boolean

  fun createComponent(
    project: Project,
    descriptor: ValueDescriptor,
    evaluationContext: EvaluationContextImpl,
    scope: CoroutineScope,
  ): JComponent

  companion object {
    private val EP_NAME = ExtensionPointName.create<CollectionVisualizer>("com.intellij.debugger.collectionVisualizer")

    fun findApplicable(collectionClass: String): CollectionVisualizer? {
      return EP_NAME.findFirstSafe { it.applicableFor(collectionClass) }
    }
  }
}
