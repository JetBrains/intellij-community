// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.ide.actions.ImportProjectAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component

suspend fun openPlatformProjectAsync(projectDirectory: VirtualFile): Project {
  return closeOpenedProjectsIfFailAsync {
    ProjectManagerEx.getInstanceEx().openProjectAsync(
      projectStoreBaseDir = projectDirectory.toNioPath(),
      options = OpenProjectTask {
        forceOpenInNewFrame = true
        useDefaultProjectAsTemplate = false
        isRefreshVfsNeeded = false
      }
    )!!
  }
}

suspend fun importProjectAsync(
  projectFile: VirtualFile,
  systemId: ProjectSystemId? = null
): Project {
  return closeOpenedProjectsIfFailAsync {
    detectOpenedProject {
      performAction(
        action = ImportProjectAction(),
        systemId = systemId,
        selectedFile = projectFile
      )
    }
  }
}

suspend fun performAction(
  action: AnAction,
  project: Project? = null,
  systemId: ProjectSystemId? = null,
  selectedFile: VirtualFile? = null
) {
  withSelectedFileIfNeeded(selectedFile) {
    withContext(Dispatchers.EDT) {
      action.actionPerformed(TestActionEvent.createTestEvent {
        when {
          ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.`is`(it) -> systemId
          CommonDataKeys.PROJECT.`is`(it) -> project
          CommonDataKeys.VIRTUAL_FILE.`is`(it) -> selectedFile
          else -> null
        }
      })
    }
  }
}

private inline fun <R> withSelectedFileIfNeeded(selectedFile: VirtualFile?, action: () -> R): R {
  if (selectedFile == null) {
    return action()
  }

  Disposer.newDisposable().use {
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, object : FileChooserFactoryImpl() {
      override fun createFileChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): FileChooserDialog {
        return object : FileChooserDialog {

          @Suppress("OVERRIDE_DEPRECATION")
          override fun choose(toSelect: VirtualFile?, project: Project?): Array<VirtualFile> {
            return choose(project, toSelect)
          }

          override fun choose(project: Project?, vararg toSelect: VirtualFile?): Array<VirtualFile> {
            return arrayOf(selectedFile)
          }
        }
      }
    }, it)
    return action()
  }
}

private inline fun detectOpenedProject(action: () -> Unit): Project {
  val projectManager = ProjectManager.getInstance()
  val openProjects = projectManager.openProjects.toHashSet()
  action()
  return projectManager.openProjects.first { it !in openProjects }
}
