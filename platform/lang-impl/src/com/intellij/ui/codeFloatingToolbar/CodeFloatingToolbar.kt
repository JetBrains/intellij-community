// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.impl.FloatingToolbar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.ui.LightweightHint
import java.awt.Point

class CodeFloatingToolbar(editor: Editor): FloatingToolbar(editor, "Floating.CodeToolbar") {

  override fun hasIgnoredParent(element: PsiElement): Boolean {
    return AdvancedSettings.getBoolean("floating.codeToolbar.hide")
           || TemplateManagerImpl.getInstance(element.project).getActiveTemplate(editor) != null
  }

  override fun shouldReviveAfterClose(): Boolean = false

  override fun shouldSurviveDocumentChange() = false

  override fun getHintPosition(hint: LightweightHint): Point {
    val isOneLineSelection = isOneLineSelection(editor)
    val offsetForHint = if (isOneLineSelection) {
      editor.selectionModel.selectionStart
    }
    else {
      getTextStart(editor, editor.caretModel.offset)
    }
    val visualPosition = editor.offsetToVisualPosition(offsetForHint)
    val hintPos = HintManagerImpl.getHintPosition(hint, editor, visualPosition, HintManager.DEFAULT)
    val verticalGap = Registry.get("floating.codeToolbar.verticalOffset").asInteger()
    val dy = if (editor.selectionModel.selectionEnd == editor.caretModel.offset && !isOneLineSelection) {
      editor.lineHeight + verticalGap
    } else {
      -(hint.component.preferredSize.height + verticalGap)
    }
    hintPos.translate(0, dy)
    return hintPos
  }

  private fun isOneLineSelection(editor: Editor): Boolean {
    val document = editor.document
    val selectionModel = editor.selectionModel
    val startLine = document.getLineNumber(selectionModel.selectionStart)
    val endLine = document.getLineNumber(selectionModel.selectionEnd)
    return startLine == endLine
  }

  private fun getTextStart(editor: Editor, offset: Int): Int {
    val document = editor.document
    val line = document.getLineNumber(offset)
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    val lineText = document.getText(TextRange.create(lineStart, lineEnd))
    val textIndex = lineText.indexOfFirst { char -> !char.isWhitespace() }
    if (textIndex < 0) return offset
    return lineStart + textIndex
  }

}