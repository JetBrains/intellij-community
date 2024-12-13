// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorLineStripeHint

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.JBColor

private class PaintEditorLineStripeHintComponentAction : DumbAwareAction() {
  @Suppress("HardCodedStringLiteral")
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.dataContext.getData(PlatformCoreDataKeys.EDITOR) ?: return
    EditorLineStripeHintComponent(editor, {
      listOf(listOf(EditorLineStripeTextRenderer("Component_1"), EditorLineStripeButtonRenderer("Button_1")),
             listOf(EditorLineStripeTextRenderer("Component_2"), EditorLineStripeButtonRenderer("Button_2")))
    }, JBColor.BLUE).redraw()
  }
}