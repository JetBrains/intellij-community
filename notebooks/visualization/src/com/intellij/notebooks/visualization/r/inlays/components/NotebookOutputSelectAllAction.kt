package com.intellij.notebooks.visualization.r.inlays.components

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.JBUI

internal class NotebookOutputSelectAllAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    // for some reason, EDITOR and HOST_EDITOR are always null for console output components. this is a workaround.
    val editor = e.getData(PlatformDataKeys.EDITOR_EVEN_IF_INACTIVE)

    e.presentation.isEnabled = editor?.contentComponent?.hasFocus() == true && editor.getUserData(NOTEBOOKS_CONSOLE_OUTPUT_KEY) == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(PlatformDataKeys.EDITOR_EVEN_IF_INACTIVE) ?: return
    editor.selectionModel.setSelection(0, editor.document.text.length)
  }
}

private val NOTEBOOKS_CONSOLE_OUTPUT_KEY = Key.create<Boolean>("NOTEBOOKS_CONSOLE_OUTPUT")

fun initOutputTextConsole(editor: Editor,
                          consoleEditor: EditorEx,
                          scrollPaneTopBorderHeight: Int) {
  updateOutputTextConsoleUI(consoleEditor, editor)
  consoleEditor.apply {
    scrollPane.border = IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(scrollPaneTopBorderHeight))
    putUserData(NOTEBOOKS_CONSOLE_OUTPUT_KEY, true)
  }

  consoleEditor.settings.isUseSoftWraps = true
}

/**
 * Changes the color scheme of consoleEditor to the color scheme of the main editor, if required.
 * [editor] is a main notebook editor, [consoleEditor] editor of particular console output.
 */
private fun updateOutputTextConsoleUI(consoleEditor: EditorEx, editor: Editor) {
  if (consoleEditor.colorsScheme != editor.colorsScheme) {
    consoleEditor.colorsScheme = editor.colorsScheme
  }
}