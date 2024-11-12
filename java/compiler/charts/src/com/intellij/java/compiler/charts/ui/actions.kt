// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import javax.swing.JComponent
import javax.swing.JLabel

interface CompilationChartsAction {
  fun isAccessible(): Boolean
  fun actionPerformed(e: MouseEvent)
  fun position(): Position
  fun label(): JLabel

  enum class Position {
    LEFT,
    RIGHT,
    LIST
  }
}

class OpenDirectoryAction(private val project: Project, private val name: String, private val close: () -> Unit) : CompilationChartsAction {
  override fun isAccessible() = true
  override fun position() = CompilationChartsAction.Position.LEFT
  override fun label(): JLabel = JLabel().apply {
    icon = Settings.Popup.MODULE_IMAGE
    toolTipText = CompilationChartsBundle.message("charts.action.open.module.directory")
    border = JBUI.Borders.emptyRight(10)
    addMouseListener(ActionMouseAdapter(this, this@OpenDirectoryAction))
  }

  override fun actionPerformed(e: MouseEvent) {
    val module = ModuleManager.getInstance(project).findModuleByName(name) ?: return
    val path = LocalFileSystem.getInstance().findFileByPath(module.moduleFilePath) ?: return
    val directory = path.parent ?: return
    close()
    OpenFileDescriptor(project, directory, -1).navigate(true)
  }
}

class OpenProjectStructureAction(
  private val project: Project,
  private val name: String,
  private val close: () -> Unit,
) : CompilationChartsAction {
  override fun isAccessible() = true
  override fun position() = CompilationChartsAction.Position.RIGHT
  override fun label(): JLabel = JLabel().apply {
    icon = Settings.Popup.EDIT_IMAGE
    toolTipText = CompilationChartsBundle.message("charts.action.open.project.structure")
    border = JBUI.Borders.emptyLeft(10)
    addMouseListener(ActionMouseAdapter(this, this@OpenProjectStructureAction))
  }

  override fun actionPerformed(e: MouseEvent) {
    val module = ModuleManager.getInstance(project).findModuleByName(name) ?: return
    val projectStructure = ProjectStructureConfigurable.getInstance(project)
    ShowSettingsUtil.getInstance().editConfigurable(project, projectStructure) {
      close()
      projectStructure.select(module.name, "Modules", true)
    }
  }
}

class ShowModuleDependenciesAction(
  private val project: Project, private val name: String,
  private val component: JComponent,
  private val close: () -> Unit,
) : CompilationChartsAction {
  private val action = ActionManager.getInstance().getAction("ShowModulesDependencies")

  override fun isAccessible() = action != null

  override fun actionPerformed(e: MouseEvent) {
    val context = object : DataContext {
      val module = ModuleManager.getInstance(project).findModuleByName(name)
      override fun getData(dataId: String): Any? = when {
        CommonDataKeys.PROJECT.`is`(dataId) -> project
        LangDataKeys.MODULE_CONTEXT_ARRAY.`is`(dataId) -> arrayOf(module)
        PlatformCoreDataKeys.CONTEXT_COMPONENT.`is`(dataId) -> component
        else -> null
      }
    }

    close()

    action.actionPerformed(AnActionEvent.createEvent(action, context, null, ActionPlaces.TOOLBAR, ActionUiKind.POPUP, e))
  }

  override fun position() = CompilationChartsAction.Position.LIST

  override fun label() = JLabel(action?.templateText).apply {
    foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
    font = font.deriveFont(Font.PLAIN)

    addMouseListener(ActionMouseAdapter(this, this@ShowModuleDependenciesAction))
  }
}

private class ActionMouseAdapter(private val parent: JLabel, private val action: CompilationChartsAction) : MouseAdapter() {
  override fun mouseClicked(e: MouseEvent) {
    action.actionPerformed(e)
  }

  override fun mouseEntered(e: MouseEvent) {
    parent.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    parent.font = parent.font.deriveFont(mapOf(TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON))
  }

  override fun mouseExited(e: MouseEvent) {
    parent.cursor = Cursor.getDefaultCursor()
    parent.font = parent.font.deriveFont(mapOf(TextAttribute.UNDERLINE to -1))
  }
}