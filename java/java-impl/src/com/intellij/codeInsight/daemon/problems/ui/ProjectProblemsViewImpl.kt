// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.ui

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.MessageCategory
import java.util.concurrent.Executor

class ProjectProblemsViewImpl(private val project: Project) : ProjectProblemsView {

  private val panel = NewErrorTreeViewPanel(project, null, false, true, null)
  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("ProblemsViewPoolExecutor", 1)

  init {
    if (Registry.`is`("project.problems.view") || ApplicationManager.getApplication().isUnitTestMode) {
      Disposer.register(project, Disposable { Disposer.dispose(panel) })
    }
  }

  override fun init(toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(panel, "", false)
    contentManager.addContent(content)
    Disposer.register(project, Disposable { contentManager.removeAllContents(true) })
  }

  override fun addProblem(file: VirtualFile, message: String, place: Navigatable) {
    panel.addMessage(MessageCategory.ERROR, arrayOf(message), file.name, place, null, null, null)
  }

  override fun executor(): Executor = executorService

  override fun removeProblems(file: VirtualFile, place: Navigatable?) {
    if (place == null) panel.removeAllInGroup(file.name)
    else panel.removeMessage(MessageCategory.ERROR, file.name, place)
  }

  override fun getProblems(file: VirtualFile): List<Navigatable> {
    val messages = panel.getNavigatableMessages(file.name) ?: return emptyList()
    return messages.mapNotNull { it.navigatable }
  }
}