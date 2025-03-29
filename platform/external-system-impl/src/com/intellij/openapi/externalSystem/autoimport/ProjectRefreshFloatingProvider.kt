// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider.Companion.EP_NAME
import com.intellij.openapi.editor.toolbar.floating.isInsideMainEditor
import com.intellij.openapi.project.Project
import com.intellij.util.containers.DisposableWrapperList
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProjectRefreshFloatingProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {
  override val autoHideable = false

  private val toolbarComponents = DisposableWrapperList<Pair<Project, FloatingToolbarComponent>>()

  override fun isApplicable(dataContext: DataContext): Boolean {
    return isInsideMainEditor(dataContext)
  }

  fun updateToolbarComponents(project: Project) {
    // init service outside of EDT if not initialized yet
    ExternalSystemProjectNotificationAware.getInstance(project)

    invokeAndWaitIfNeeded {
      for ((componentProject, component) in toolbarComponents) {
        if (componentProject === project) {
          updateToolbarComponent(componentProject, component)
        }
      }
    }
  }

  private fun updateToolbarComponent(project: Project, component: FloatingToolbarComponent) {
    val notificationAware = ExternalSystemProjectNotificationAware.getInstance(project)

    when (notificationAware.isNotificationVisible()) {
      true -> component.scheduleShow()
      else -> component.scheduleHide()
    }
  }

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
    val project = dataContext.getData(PROJECT) ?: return
    toolbarComponents.add(project to component, parentDisposable)

    updateToolbarComponent(project, component)
  }

  internal class Listener : ExternalSystemProjectNotificationAware.Listener {
    override fun onNotificationChanged(project: Project) {
      EP_NAME.findExtension(ProjectRefreshFloatingProvider::class.java)
        ?.updateToolbarComponents(project)
    }
  }

  companion object {
    @Language("devkit-action-id") private const val ACTION_GROUP = "ExternalSystem.ProjectRefreshActionGroup"
  }
}
