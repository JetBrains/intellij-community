// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  fun updateToolbarComponents(project: Project, notificationAware: ProjectNotificationAware) {
    forEachToolbarComponent(project) { updateToolbarComponent(it, notificationAware) }
  }

  fun updateToolbarComponent(component: FloatingToolbarComponent, notificationAware: ProjectNotificationAware) {
    when (notificationAware.isNotificationVisible()) {
      true -> component.scheduleShow()
      else -> component.scheduleHide()
    }
  }

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
    val project = dataContext.getData(PROJECT) ?: return
    toolbarComponents.add(project to component, parentDisposable)
    val notificationAware = ProjectNotificationAware.getInstance(project)
    updateToolbarComponent(component, notificationAware)
  }

  private fun forEachToolbarComponent(project: Project, consumer: (FloatingToolbarComponent) -> Unit) {
    for ((componentProject, component) in toolbarComponents) {
      if (componentProject === project) {
        consumer(component)
      }
    }
  }

  companion object {
    const val ACTION_GROUP = "ExternalSystem.ProjectRefreshActionGroup"

    private fun getProvider(): ProjectRefreshFloatingProvider {
      return FloatingToolbarProvider.getProvider()
    }

    fun updateToolbarComponents(project: Project, notificationAware: ProjectNotificationAware) {
      getProvider().updateToolbarComponents(project, notificationAware)
    }
  }
}