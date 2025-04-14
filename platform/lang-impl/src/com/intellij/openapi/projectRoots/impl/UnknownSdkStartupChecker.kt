// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity

private class UnknownSdkStartupChecker : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // avoid crazy background activity in tests
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    blockingContext {
      checkUnknownSdks(project)
    }

    ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener {
      checkUnknownSdks(project)
    }

    UnknownSdkResolver.EP_NAME.addExtensionPointListener(object: ExtensionPointListener<UnknownSdkResolver> {
      override fun extensionAdded(extension: UnknownSdkResolver, pluginDescriptor: PluginDescriptor) {
        checkUnknownSdks(project)
      }

      override fun extensionRemoved(extension: UnknownSdkResolver, pluginDescriptor: PluginDescriptor) {
        checkUnknownSdks(project)
      }
    }, project)

    project.workspaceModel.eventLog.collect { event ->
      if (event.getChanges(SdkEntity::class.java).any() || event.getChanges(ContentRootEntity::class.java).any()) {
        checkUnknownSdks(project)
      }
    }
  }

  private fun checkUnknownSdks(project: Project) {
    if (project.isDisposed || project.isDefault) return
    //TODO: workaround for tests, right not it can happen that project.earlyDisposable is null with @NotNull annotation
    if (project is ProjectEx && kotlin.runCatching { project.earlyDisposable }.isFailure) return
    UnknownSdkTracker.getInstance(project).updateUnknownSdks()
  }
}
