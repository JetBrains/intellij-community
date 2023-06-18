// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.FloatingToolbar
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.LightweightHint
import java.awt.Point

/**
 * Represents floating toolbar which is shown for selected text inside editor.
 * Toolbar is visible only if mouse was used for the selection.
 */
class CodeFloatingToolbar(editor: Editor): FloatingToolbar(editor, "Floating.CodeToolbar") {

  companion object {
    private val FLOATING_TOOLBAR = Key<CodeFloatingToolbar>("floating.codeToolbar")
    
    @JvmStatic
    fun getToolbar(editor: Editor): CodeFloatingToolbar? {
      return editor.getUserData(FLOATING_TOOLBAR)
    }
  }

  init {
    editor.putUserData(FLOATING_TOOLBAR, this)
    Disposer.register(this) { editor.putUserData(FLOATING_TOOLBAR, null) }
  }

  override fun hasIgnoredParent(element: PsiElement): Boolean {
    return AdvancedSettings.getBoolean("floating.codeToolbar.hide")
           || !element.isWritable
           || TemplateManagerImpl.getInstance(element.project).getActiveTemplate(editor) != null
  }

  override fun disableForDoubleClickSelection(): Boolean = true

  override fun shouldReviveAfterClose(): Boolean = false

  override fun shouldSurviveDocumentChange(): Boolean = false

  override fun hideByOtherHints(): Boolean = false

  override fun getHintPosition(hint: LightweightHint): Point {
    val selectionEnd = editor.selectionModel.selectionEnd
    val selectionStart = editor.selectionModel.selectionStart
    val isOneLineSelection = isOneLineSelection(editor)
    val isBelow = isOneLineSelection
                  || selectionEnd == editor.caretModel.offset
                  || Registry.get("floating.codeToolbar.showBelow").asBoolean() && isSelectionEndVisible(editor)
    val anchorOffset = if (isBelow) selectionEnd else selectionStart
    val offsetForHint = getTextStart(editor, anchorOffset)
    val visualPosition = editor.offsetToVisualPosition(offsetForHint)
    val hintPoint = HintManagerImpl.getHintPosition(hint, editor, visualPosition, HintManager.DEFAULT)
    val verticalGap = Registry.get("floating.codeToolbar.verticalOffset").asInteger()
    val dy = if (isBelow) {
      editor.lineHeight + verticalGap
    } else {
      -(hint.component.preferredSize.height + verticalGap)
    }
    hintPoint.translate(0, dy)
    return hintPoint
  }

  private fun isSelectionEndVisible(editor: Editor): Boolean {
    return editor.offsetToXY(editor.selectionModel.selectionEnd) in editor.scrollingModel.visibleArea
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


  override fun createActionGroup(): ActionGroup? {
    val contextAwareActionGroupId = getContextAwareGroupId()
    val mainActionGroup = CustomActionsSchema.getInstance().getCorrectedAction(contextAwareActionGroupId) as? ActionGroup ?: return super.createActionGroup()
    val showIntentionsAction = CustomActionsSchema.getInstance().getCorrectedAction("ShowIntentionActions") ?: error("Can't find ShowIntentionActions action")
    val configurationGroup = createConfigureGroup(contextAwareActionGroupId)
    return DefaultActionGroup(showIntentionsAction, Separator.create(), mainActionGroup, Separator.create(), configurationGroup)
  }

  private fun getContextAwareGroupId(): String {
    val project = editor.project ?: return defaultActionGroupId
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    val elementAtOffset = psiFile?.findElementAt(editor.caretModel.primaryCaret.offset)
    val targetLanguage = elementAtOffset?.language ?: return defaultActionGroupId
    return FloatingToolbarCustomizer.findActionGroupFor(targetLanguage) ?: defaultActionGroupId
  }

  private fun createConfigureGroup(customizableGroupId: String): ActionGroup {
    val customizeAction = CustomizeCodeFloatingToolbarAction(customizableGroupId)
    val disableAction = CustomActionsSchema.getInstance().getCorrectedAction("Floating.CodeToolbar.Disable") ?: error("Can't find Floating.CodeToolbar.Disable action")
    return MoreActionGroup().apply {
      addAction(customizeAction)
      addAction(disableAction)
    }
  }
}