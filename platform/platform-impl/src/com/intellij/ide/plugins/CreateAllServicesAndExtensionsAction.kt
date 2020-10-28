// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.DumbAware
import com.intellij.serviceContainer.ComponentManagerImpl

@Suppress("HardCodedStringLiteral")
private class CreateAllServicesAndExtensionsAction : AnAction("Create All Services And Extensions"), DumbAware {
  companion object {
    @JvmStatic
    fun createAllServicesAndExtensions() {
      runModalTask("Creating All Services And Extensions", cancellable = true) {
        checkContainer(ApplicationManager.getApplication() as ComponentManagerImpl)
        for (project in ProjectUtil.getOpenProjects()) {
          checkContainer(project as ComponentManagerImpl)
        }
      }
    }

    private fun checkContainer(container: ComponentManagerImpl) {
      ComponentManagerImpl.createAllServices(container)
      container.extensionArea.processExtensionPoints {
        // requires read action
        if (it.name != "com.intellij.favoritesListProvider") {
          it.extensionList
        }
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    createAllServicesAndExtensions()
  }
}