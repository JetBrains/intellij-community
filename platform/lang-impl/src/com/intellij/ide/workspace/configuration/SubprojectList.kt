// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace.configuration

import com.intellij.ide.workspace.getAllSubprojects
import com.intellij.ide.workspace.getHandlers
import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.util.SystemProperties
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JList
import kotlin.io.path.pathString

internal class SubprojectList(private val currentProject: Project?) {

  private val listModel: CollectionListModel<Item>
  private val projectList: JBList<Item>

  init {
    val subprojects = currentProject?.let { getAllSubprojects(currentProject) } ?: emptyList()
    listModel = CollectionListModel(subprojects.map { Item(it.name, it.projectPath, it.handler.subprojectIcon) })
    projectList = JBList(listModel).apply {
      cellRenderer = Renderer().apply { iconTextGap = 3 }
    }
  }

  fun createDecorator(): ToolbarDecorator = ToolbarDecorator.createDecorator(projectList)
    .setPanelBorder(IdeBorderFactory.createTitledBorder(LangBundle.message("border.title.linked.projects")))
    .disableUpDownActions()
    .setAddActionName(LangBundle.message("action.add.projects.text"))
    .setAddAction { addProjects() }

  val projectPaths: List<String>
    get()  = listModel.items.map { it.path }

  private data class Item(@NlsSafe val name: String, @NlsSafe val path: String, val icon: Icon?)

  private class Renderer: ColoredListCellRenderer<Item>() {
    override fun customizeCellRenderer(list: JList<out Item>, value: Item?, index: Int, selected: Boolean, hasFocus: Boolean) {
      value ?: return
      icon = value.icon
      append(value.name + "   ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      @Suppress("HardCodedStringLiteral") val userHome = SystemProperties.getUserHome()
      append(Path.of(value.path).parent.pathString.replaceFirst(userHome, "~"), SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }

  private fun addProjects() {
    val files = browseForProjects(currentProject)
    val allItems = listModel.items
    for (file in files) {
      if (allItems.any { it.path == file.path }) continue
      val handler = getHandlers(file).firstOrNull() ?: continue
      listModel.add(Item(file.name, file.path, handler.subprojectIcon))
    }
  }
}