// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.project.Project
import com.intellij.util.containers.DisposableWrapperList

class ProjectRefreshFloatingProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {

  override val autoHideable = false

  private val toolbarComponents = DisposableWrapperList<Pair<Project, FloatingToolbarComponent>>()

  private fun updateToolbarComponents(project: Project) {
    forEachToolbarComponent(project) {
      updateToolbarComponent(project, it)
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

  private fun forEachToolbarComponent(project: Project, consumer: (FloatingToolbarComponent) -> Unit) {
    for ((componentProject, component) in toolbarComponents) {
      if (componentProject === project) {
        consumer(component)
      }
    }
  }

  class Listener : ExternalSystemProjectNotificationAware.Listener {
    override fun onNotificationChanged(project: Project) {
      FloatingToolbarProvider.getProvider<ProjectRefreshFloatingProvider>()
        .updateToolbarComponents(project)
    }
  }

  companion object {
    private const val ACTION_GROUP = "ExternalSystem.ProjectRefreshActionGroup"
  }
}