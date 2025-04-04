// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.MultiMap

internal class DumpWatchedRootsAction : DumbAwareAction() {
  @Suppress("HardCodedStringLiteral")
  override fun actionPerformed(e: AnActionEvent) {
    val projects = ProjectManager.getInstance().openProjects
    val roots2Project = MultiMap<Pair<String, Boolean>, Project>()
    for (project in projects) {
      val roots = (ProjectRootManager.getInstance(project) as ProjectRootManagerComponent).rootsToWatch.map { it.rootPath to it.isToWatchRecursively }
      for (root in roots) {
        roots2Project.putValue(root, project)
      }
    }

    val roots = roots2Project.entrySet().map {
      Root(it.key.first, it.key.second, it.value.map { p -> p.name + "-" + p.locationHash }.sorted())
    }.sortedBy { it.path }

    val baseListPopupStep = object : BaseListPopupStep<Root>("Registered Roots", roots) {

      override fun isSpeedSearchEnabled() = true

      override fun getTextFor(value: Root?): String {
        return if (value == null) ""
        else "${StringUtil.shortenPathWithEllipsis(value.path, 100)} ${if (value.recursive) "recursive" else "non-recursive" }  (${value.projects.joinToString(separator = ",")})"
      }
    }
    val popup = JBPopupFactory.getInstance().createListPopup(baseListPopupStep)
    popup.showInBestPositionFor(e.dataContext)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private data class Root(val path: String, val recursive: Boolean, val projects: List<String>)
}