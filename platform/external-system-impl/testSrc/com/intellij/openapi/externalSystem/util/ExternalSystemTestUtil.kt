// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component


suspend fun performOpenAction(
  action: AnAction,
  project: Project? = null,
  systemId: ProjectSystemId? = null,
  selectedFile: VirtualFile? = null
): Project {
  return closeOpenedProjectsIfFailAsync {
    detectOpenedProject {
      performAction(action, project, systemId, selectedFile)
    }
  }
}

suspend fun performAction(
  action: AnAction,
  project: Project? = null,
  systemId: ProjectSystemId? = null,
  selectedFile: VirtualFile? = null,
) {
  withSelectedFileIfNeeded(selectedFile) {
    val event = TestActionEvent.createTestEvent {
      when {
        ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.`is`(it) -> systemId
        CommonDataKeys.PROJECT.`is`(it) -> project
        CommonDataKeys.VIRTUAL_FILE.`is`(it) -> selectedFile
        else -> null
      }
    }
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        action.actionPerformed(event)
      }
    }
  }
}

suspend fun performActionAsync(
  action: suspend (AnActionEvent) -> Unit,
  project: Project? = null,
  systemId: ProjectSystemId? = null,
  selectedFile: VirtualFile? = null,
) {
  withSelectedFileIfNeeded(selectedFile) {
    val event = TestActionEvent.createTestEvent {
      when {
        ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.`is`(it) -> systemId
        CommonDataKeys.PROJECT.`is`(it) -> project
        CommonDataKeys.VIRTUAL_FILE.`is`(it) -> selectedFile
        else -> null
      }
    }
    withContext(Dispatchers.EDT) {
      action(event)
    }
  }
}

private inline fun <R> withSelectedFileIfNeeded(selectedFile: VirtualFile?, action: () -> R): R {
  if (selectedFile == null) {
    return action()
  }

  Disposer.newDisposable().use {
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, object : FileChooserFactoryImpl() {
      override fun createFileChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): FileChooserDialog =
        object : FileChooserDialog {
          override fun choose(project: Project?, vararg toSelect: VirtualFile?): Array<VirtualFile> = arrayOf(selectedFile)
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
