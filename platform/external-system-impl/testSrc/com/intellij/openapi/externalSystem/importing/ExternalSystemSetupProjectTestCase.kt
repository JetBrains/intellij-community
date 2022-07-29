// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.actions.ImportModuleAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
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
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import java.awt.Component

interface ExternalSystemSetupProjectTestCase {
  data class ProjectInfo(val projectFile: VirtualFile, val modules: List<String>) {
    constructor(projectFile: VirtualFile, vararg modules: String) : this(projectFile, modules.toList())
  }

  fun generateProject(id: String): ProjectInfo

  fun getSystemId(): ProjectSystemId

  fun assertDefaultProjectSettings(project: Project) {}

  suspend fun assertDefaultProjectState(project: Project) {}

  suspend fun attachProject(project: Project, projectFile: VirtualFile): Project

  suspend fun attachProjectFromScript(project: Project, projectFile: VirtualFile): Project

  suspend fun waitForImport(action: suspend () -> Project): Project

  fun openPlatformProjectFrom(projectDirectory: VirtualFile): Project {
    return ProjectManagerEx.getInstanceEx().openProject(
      projectStoreBaseDir = projectDirectory.toNioPath(),
      options = OpenProjectTask {
        forceOpenInNewFrame = true
        useDefaultProjectAsTemplate = false
        isRefreshVfsNeeded = false
      }
    )!!
  }

  suspend fun openProjectFrom(virtualFile: VirtualFile) = ProjectUtil.openOrImportAsync(virtualFile.toNioPath())!!

  suspend fun importProjectFrom(projectFile: VirtualFile): Project {
    val projectManager = ProjectManager.getInstance()
    val openProjects = projectManager.openProjects.toHashSet()
    performAction(action = ImportModuleAction(), selectedFile = projectFile)
    return projectManager.openProjects.first { it !in openProjects }
  }

  fun assertModules(project: Project, vararg projectInfo: ProjectInfo) {
    val expectedNames = projectInfo.flatMap { it.modules }.toList().sorted()
    val actual = ModuleManager.getInstance(project).modules
    val actualNames = actual.map { it.name }.toList().sorted()

    assertEquals(
      expectedNames.joinToString(separator = System.lineSeparator()),
      actualNames.joinToString(separator = System.lineSeparator())
    )
  }

  suspend fun performAction(action: AnAction, project: Project? = null, selectedFile: VirtualFile? = null) {
    withSelectedFileIfNeeded(selectedFile) {
      withContext(Dispatchers.EDT) {
        action.actionPerformed(TestActionEvent {
          when {
            ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.`is`(it) -> getSystemId()
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

  suspend fun cleanupProjectTestResources(project: Project) {}

  suspend fun Project.use(save: Boolean = false, action: suspend (Project) -> Unit) {
    try {
      action(this)
    }
    finally {
      try {
        cleanupProjectTestResources(this)
      }
      finally {
        val project = this
        val projectManager = ProjectManagerEx.getInstanceEx()
        if (save) {
          saveSettings(project, forceSavingAllSettings = true)
        }
        projectManager.forceCloseProjectAsync(project, save = save)
      }
    }
  }
}