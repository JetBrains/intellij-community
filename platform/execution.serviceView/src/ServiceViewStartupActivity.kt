// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.launch

internal class ServiceViewStartupActivity private constructor() : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) : Unit = blockingContext {
    if (!shouldEnableServicesViewInCurrentEnvironment()) return@blockingContext

    if (!ServiceViewContributor.CONTRIBUTOR_EP_NAME.hasAnyExtensions()) {
      ServiceViewContributor.CONTRIBUTOR_EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ServiceViewContributor<*>> {
        override fun extensionAdded(extension: ServiceViewContributor<*>, pluginDescriptor: PluginDescriptor) {
          ServiceViewManager.getInstance(project)
        }
      }, project)
    }
    else {
      // init manager to check availability on background thread and register tool window
      ToolWindowManager.Companion.getInstance(project).invokeLater {
        (project as ComponentManagerEx).getCoroutineScope().launch { ServiceViewManager.getInstance(project) }
      }
    }
  }
}