// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.editor.TextAnnotationGutterProvider
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * Created by Yuli Fiterman on 9/16/2016.
 */
class ConsolePromptDecorator(private val myEditorEx: EditorEx) : EditorLinePainter(), TextAnnotationGutterProvider {

  var mainPrompt: String = "> "
    get() = if (myEditorEx.isRendererMode) "" else field
    set(mainPrompt) {
      val mainPromptWrapped = wrapPrompt(mainPrompt)
      if (this.mainPrompt != mainPromptWrapped) {
        // to be compatible with LanguageConsoleView we should reset the indent prompt
        indentPrompt = ""
        field = mainPromptWrapped
        update()
      }
    }

  var promptAttributes: ConsoleViewContentType = ConsoleViewContentType.USER_INPUT
    set(promptAttributes) {
      field = promptAttributes
      myEditorEx.colorsScheme.setColor(promptColor, promptAttributes.attributes.foregroundColor)
      update()
    }

  var indentPrompt: String = ""
    get() = if (myEditorEx.isRendererMode) "" else field
    set(indentPrompt) {
      val indentPromptWrapped = wrapPrompt(indentPrompt)
      field = indentPromptWrapped
      update()
    }

  init {
    myEditorEx.colorsScheme.setColor(promptColor, this.promptAttributes.attributes.foregroundColor)
  }

  // always add space to the prompt otherwise it may look ugly
  private fun wrapPrompt(prompt: String) = if (!prompt.endsWith(" ")) "$prompt " else prompt

  override fun getLineExtensions(project: Project, file: VirtualFile, lineNumber: Int): Collection<LineExtensionInfo>? = null

  override fun getLineText(line: Int, editor: Editor): String? {
    when {
      line == 0 -> return mainPrompt
      line > 0 -> return indentPrompt
      else -> return null
    }
  }

  override fun getToolTip(line: Int, editor: Editor): String?  = null

  override fun getStyle(line: Int, editor: Editor): EditorFontType = EditorFontType.CONSOLE_PLAIN

  override fun getColor(line: Int, editor: Editor): ColorKey = promptColor

  override fun getBgColor(line: Int, editor: Editor): Color {
    var backgroundColor: Color? = this.promptAttributes.attributes.backgroundColor
    if (backgroundColor == null) {
      backgroundColor = myEditorEx.backgroundColor
    }
    return backgroundColor
  }

  override fun getPopupActions(line: Int, editor: Editor): List<AnAction>? = null

  override fun gutterClosed() {}

  override fun useMargin(): Boolean = false

  fun update() {
    UIUtil.invokeLaterIfNeeded {
      if (!myEditorEx.isDisposed) {
        myEditorEx.gutterComponentEx.revalidateMarkup()
      }
    }
  }

  companion object {
    private val promptColor = ColorKey.createColorKey("CONSOLE_PROMPT_COLOR")
  }
}

