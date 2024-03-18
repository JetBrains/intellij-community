// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.ui.codereview.comment.createEditActionsConfig
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.swing.JComponent

object EditableComponentFactory {
  fun <VM : Any> create(cs: CoroutineScope, component: JComponent, editingVm: Flow<VM?>,
                        editorComponentSupplier: CoroutineScope.(VM) -> JComponent): JComponent {
    return Wrapper().apply {
      bindContentIn(cs, editingVm) { vm ->
        if (vm != null) editorComponentSupplier(vm) else component
      }
    }
  }

  fun wrapTextComponent(cs: CoroutineScope, component: JComponent, editVmFlow: Flow<CodeReviewTextEditingViewModel?>,
                        afterSave: () -> Unit = {}) =
    create(cs, component, editVmFlow) { editVm ->
      val actions = createEditActionsConfig(editVm, afterSave)
      val editor = CodeReviewCommentTextFieldFactory.createIn(this, editVm, actions)
      editor
    }
}