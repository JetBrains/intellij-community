// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver
import com.intellij.openapi.startup.ProjectPostStartupActivity

private class UnknownSdkStartupChecker : ProjectPostStartupActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // avoid crazy background activity in tests
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    checkUnknownSdks(project)

    UnknownSdkResolver.EP_NAME.addExtensionPointListener(object: ExtensionPointListener<UnknownSdkResolver> {
      override fun extensionAdded(extension: UnknownSdkResolver, pluginDescriptor: PluginDescriptor) {
        checkUnknownSdks(project)
      }

      override fun extensionRemoved(extension: UnknownSdkResolver, pluginDescriptor: PluginDescriptor) {
        checkUnknownSdks(project)
      }
    }, project)

    project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object: ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        checkUnknownSdks(event.project)
      }
    })

    ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener {
      checkUnknownSdks(project)
    }

    project.messageBus.simpleConnect().subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkAdded(jdk: Sdk) {
        checkUnknownSdks(project)
      }

      override fun jdkRemoved(jdk: Sdk) {
        checkUnknownSdks(project)
      }

      override fun jdkNameChanged(jdk: Sdk, previousName: String) {
        checkUnknownSdks(project)
      }
    })
  }

  private fun checkUnknownSdks(project: Project) {
    if (project.isDisposed || project.isDefault) return
    //TODO: workaround for tests, right not it can happen that project.earlyDisposable is null with @NotNull annotation
    if (project is ProjectEx && kotlin.runCatching { project.earlyDisposable }.isFailure) return
    UnknownSdkTracker.getInstance(project).updateUnknownSdks()
  }
}
