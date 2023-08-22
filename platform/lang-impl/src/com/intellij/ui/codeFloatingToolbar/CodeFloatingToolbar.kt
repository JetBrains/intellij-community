// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.FloatingToolbar
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.LightweightHint
import kotlinx.coroutines.CoroutineScope
import java.awt.Point
import javax.swing.JComponent

/**
 * Represents floating toolbar which is shown for selected text inside editor.
 * Toolbar is visible only if mouse was used for the selection.
 */
class CodeFloatingToolbar(
  editor: Editor,
  coroutineScope: CoroutineScope
): FloatingToolbar(editor, coroutineScope) {

  companion object {
    private val FLOATING_TOOLBAR = Key<CodeFloatingToolbar>("floating.codeToolbar")
    
    @JvmStatic
    fun getToolbar(editor: Editor?): CodeFloatingToolbar? {
      return editor?.getUserData(FLOATING_TOOLBAR)
    }
  }

  init {
    editor.putUserData(FLOATING_TOOLBAR, this)
    Disposer.register(this) { editor.putUserData(FLOATING_TOOLBAR, null) }
  }

  override fun hasIgnoredParent(element: PsiElement): Boolean {
    return !element.isWritable || TemplateManager.getInstance(element.project).getActiveTemplate(editor) != null
  }

  override fun isEnabled(): Boolean {
    val selection = editor.selectionModel
    if (!selection.hasSelection()) return false
    val range = editor.calculateVisibleRange()
    if (selection.selectionStart !in range && selection.selectionEnd !in range) return false
    return editor.document.isWritable && !AdvancedSettings.getBoolean("floating.codeToolbar.hide")
  }

  override fun disableForDoubleClickSelection(): Boolean = true

  override fun shouldSurviveDocumentChange(): Boolean = false

  override fun hideByOtherHints(): Boolean = false

  val hintComponent: JComponent?
    get() = hint?.component

  override fun getHintPosition(hint: LightweightHint): Point {
    val selectionEnd = editor.selectionModel.selectionEnd
    val selectionStart = editor.selectionModel.selectionStart
    val isOneLineSelection = isOneLineSelection(editor)
    val isBelow = shouldBeUnderSelection(selectionEnd)
    val offsetForHint = when {
      isOneLineSelection -> selectionStart
      isBelow -> getOffsetForLine(editor, getLineByVisualStart(editor, selectionEnd, true))
      else -> getOffsetForLine(editor, getLineByVisualStart(editor, selectionStart, false))
    }
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

  private fun shouldBeUnderSelection(selectionEnd: Int): Boolean {
    val showUnderSelection = selectionEnd == editor.caretModel.offset || Registry.get("floating.codeToolbar.showBelow").asBoolean()
    val preferredOffset = if (!showUnderSelection) editor.selectionModel.selectionStart else editor.selectionModel.selectionEnd
    if (editor.offsetToXY(preferredOffset) in editor.scrollingModel.visibleArea) {
      return showUnderSelection
    } else {
      return !showUnderSelection
    }
  }

  private fun isOneLineSelection(editor: Editor): Boolean {
    val document = editor.document
    val selectionModel = editor.selectionModel
    val startLine = document.getLineNumber(selectionModel.selectionStart)
    val endLine = document.getLineNumber(selectionModel.selectionEnd)
    return startLine == endLine
  }

  private fun getOffsetForLine(editor: Editor, line: Int): Int {
    val document = editor.document
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    val lineText = document.getText(TextRange.create(lineStart, lineEnd))
    val textIndex = lineText.indexOfFirst { char -> !char.isWhitespace() }
    if (textIndex < 0) return lineStart
    return lineStart + textIndex
  }

  private fun getLineByVisualStart(editor: Editor, offset: Int, skipLineStartOffset: Boolean): Int {
    val visualPosition = editor.offsetToVisualPosition(offset)
    val skipCurrentLine = skipLineStartOffset && visualPosition.column == 0
    val line = if (skipCurrentLine) maxOf(visualPosition.line - 1, 0) else visualPosition.line
    val lineStartPosition = VisualPosition(line, 0)
    return editor.visualToLogicalPosition(lineStartPosition).line
  }

  override fun createActionGroup(): ActionGroup? {
    val contextAwareActionGroupId = getContextAwareGroupId(editor) ?: return null
    val mainActionGroup = CustomActionsSchema.getInstance().getCorrectedAction(contextAwareActionGroupId) ?: error("Can't find groupId action")
    val showIntentionsAction = CustomActionsSchema.getInstance().getCorrectedAction("ShowIntentionActions")
                               ?: error("Can't find ShowIntentionActions action")
    val configurationGroup = createConfigureGroup(contextAwareActionGroupId)
    return DefaultActionGroup(showIntentionsAction, Separator.create(), mainActionGroup, Separator.create(), configurationGroup)
  }

  private fun getContextAwareGroupId(editor: Editor): String? {
    val project = editor.project ?: return null
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    val elementAtOffset = psiFile?.findElementAt(editor.caretModel.primaryCaret.offset)
    val targetLanguage = elementAtOffset?.language ?: return null
    return FloatingToolbarCustomizer.findActionGroupFor(targetLanguage)
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