// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.ui

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.pom.Navigatable
import java.util.concurrent.Executor


interface ProjectProblemsView {

  object SERVICE {
    fun getInstance(project: Project): ProjectProblemsView = ServiceManager.getService(project, ProjectProblemsView::class.java)
  }

  fun addProblem(file: VirtualFile, message: String, place: Navigatable)

  fun removeProblems(file: VirtualFile, place: Navigatable? = null)

  fun getProblems(file: VirtualFile): List<Navigatable>

  fun init(toolWindow: ToolWindow)

  fun executor(): Executor
}