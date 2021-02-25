// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.util.containers.MultiMap

internal class DumpWatchedRootsAction : AnAction() {
  @Suppress("HardCodedStringLiteral")
  override fun actionPerformed(e: AnActionEvent) {
    val projects = ProjectManager.getInstance().openProjects
    val roots2Project = MultiMap<String, Project>()
    for (project in projects) {
      val roots = (ProjectRootManager.getInstance(project) as ProjectRootManagerComponent).rootsToWatch.map { it.rootPath }
      for (root in roots) {
        roots2Project.putValue(root, project)
      }
    }

    val roots = roots2Project.entrySet().map { Root(it.key, it.value.map { p -> p.name + "-" + p.locationHash }.sorted()) }.sortedBy { it.path }
    val popup = JBPopupFactory.getInstance().createListPopup(BaseListPopupStep("Registered Roots", roots))
    popup.showInBestPositionFor(e.dataContext)
  }

  private data class Root(val path: String, val projects: List<String>)
}