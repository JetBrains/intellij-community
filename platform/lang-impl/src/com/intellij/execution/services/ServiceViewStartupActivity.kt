// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.launch

internal class ServiceViewStartupActivity private constructor() : ProjectPostStartupActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (ServiceViewContributor.CONTRIBUTOR_EP_NAME.extensionList.isEmpty()) {
      ServiceViewContributor.CONTRIBUTOR_EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ServiceViewContributor<*>> {
        override fun extensionAdded(extension: ServiceViewContributor<*>, pluginDescriptor: PluginDescriptor) {
          ServiceViewManager.getInstance(project)
        }
      }, project)
    }
    else {
      // init manager to check availability on background thread and register tool window
      ToolWindowManager.getInstance(project).invokeLater {
        (project as ProjectEx).coroutineScope.launch { ServiceViewManager.getInstance(project) }
      }
    }
  }
}