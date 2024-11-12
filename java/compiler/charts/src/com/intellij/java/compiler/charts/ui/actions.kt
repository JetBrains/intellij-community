// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsBundle
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.event.MouseAdapter
import javax.swing.JLabel

interface CompilationChartsAction {
  fun isAccessible(): Boolean
  fun actionPerformed()
  fun id(): String
  fun position(): Position
  fun label(): JLabel

  enum class Position {
    LEFT,
    RIGHT,
    LIST
  }
}

class OpenDirectoryAction(private val project: Project, private val name: String) : CompilationChartsAction {
  override fun isAccessible() = true
  override fun id() = "open.module.directory"
  override fun position() = CompilationChartsAction.Position.LEFT
  override fun label(): JLabel = JLabel().apply {
    icon = Settings.Popup.MODULE_IMAGE
    toolTipText = CompilationChartsBundle.message("charts.action.open.module.directory")
    border = JBUI.Borders.emptyRight(10)
    addMouseListener(ActionMouseAdapter(this, this@OpenDirectoryAction))
  }

  override fun actionPerformed() {
    val module = ModuleManager.getInstance(project).findModuleByName(name) ?: return
    val path = LocalFileSystem.getInstance().findFileByPath(module.moduleFilePath) ?: return
    val directory = path.parent ?: return
    OpenFileDescriptor(project, directory, -1).navigate(true)
  }
}

private class ActionMouseAdapter(private val parent: JLabel, private val action: CompilationChartsAction) : MouseAdapter() {
  override fun mouseClicked(e: java.awt.event.MouseEvent?) {
    action.actionPerformed()
    //close()
  }

  override fun mouseEntered(e: java.awt.event.MouseEvent?) {
    parent.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
  }

  override fun mouseExited(e: java.awt.event.MouseEvent?) {
    parent.cursor = Cursor.getDefaultCursor()
  }
}