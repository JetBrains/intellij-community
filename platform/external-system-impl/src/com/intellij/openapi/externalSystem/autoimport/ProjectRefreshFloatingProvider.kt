// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.isInsideMainEditor
import com.intellij.platform.externalSystem.impl.ExternalSystemImplCoroutineScope.esCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

@Language("devkit-action-id") private const val ACTION_GROUP = "ExternalSystem.ProjectRefreshActionGroup"

@ApiStatus.Internal
class ProjectRefreshFloatingProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {

  override val autoHideable: Boolean = false

  override fun isApplicable(dataContext: DataContext): Boolean {
    return isInsideMainEditor(dataContext)
  }

  private fun updateToolbarComponent(project: Project, component: FloatingToolbarComponent) {
    project.esCoroutineScope.launch {
      val notificationAware = project.serviceAsync<ExternalSystemProjectNotificationAware>()
      withContext(Dispatchers.EDT) {
        when (notificationAware.isNotificationVisible()) {
          true -> component.scheduleShow()
          else -> component.scheduleHide()
        }
      }
    }
  }

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
    val myProject = dataContext.getData(PROJECT) ?: return

    application.messageBus.connect(parentDisposable)
      .subscribe(ExternalSystemProjectNotificationAware.TOPIC, object : ExternalSystemProjectNotificationAware.Listener {
        override fun onNotificationChanged(project: Project) {
          if (project === myProject) {
            updateToolbarComponent(project, component)
          }
        }
      })

    updateToolbarComponent(myProject, component)
  }
}