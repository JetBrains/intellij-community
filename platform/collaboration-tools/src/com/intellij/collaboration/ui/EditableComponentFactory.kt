// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import javax.swing.JComponent

object EditableComponentFactory {
  fun <VM : Any> create(cs: CoroutineScope, component: JComponent, editingVm: Flow<VM?>,
                        editorComponentSupplier: (CoroutineScope, VM) -> JComponent): JComponent {
    return Wrapper().apply {
      isOpaque = false

      cs.launch(Dispatchers.Main.immediate) {
        editingVm.collectLatest { vm ->
          removeAll()

          if (vm != null) {
            coroutineScope {
              val editor = editorComponentSupplier(this, vm)
              setContent(editor)
              CollaborationToolsUIUtil.focusPanel(editor)
              repaint()
              awaitCancellation()
            }
          }
          else {
            setContent(component)
            repaint()
          }
        }
      }
    }
  }
}