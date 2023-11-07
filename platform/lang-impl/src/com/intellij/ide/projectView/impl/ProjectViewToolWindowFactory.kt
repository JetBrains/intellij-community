// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.util.RunOnceUtil.runOnceForProject
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.module.GeneralModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.Icon
import javax.swing.tree.TreePath

@VisibleForTesting
class ProjectViewToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    (ProjectView.getInstance(project) as ProjectViewImpl).setupImpl(toolWindow)
  }

  override val icon: Icon
    get() = AllIcons.Toolwindows.ToolWindowProject

  override fun init(window: ToolWindow) {
    if (!Registry.`is`("ide.open.project.view.on.startup", true)) {
      return
    }

    val project = window.project
    if (isNotificationSilentMode(project)) {
      return
    }

    val manager = ToolWindowManager.getInstance(project)
    manager.invokeLater {
      runOnceForProject(project, "OpenProjectViewOnStart") {
        if (manager.activeToolWindowId != null ||
            java.lang.Boolean.TRUE != project.getUserData<Boolean>(FileEditorManagerImpl.NOTHING_WAS_OPENED_ON_START)) {
          return@runOnceForProject
        }
        window.activate {
          val modules = ModuleManager.getInstance(project).modules
          if (modules.size == 1 && GeneralModuleType.TYPE_ID == modules[0].moduleTypeName) {
            return@activate
          }

          val pane = ProjectView.getInstance(project).currentProjectViewPane
          val tree = pane?.tree
          if (tree != null) {
            TreeUtil.promiseSelectFirst(tree).onSuccess { path: TreePath? -> tree.expandPath(path) }
          }
        }
      }
    }
  }
}
