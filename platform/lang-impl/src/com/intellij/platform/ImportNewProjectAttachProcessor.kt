// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.CommonBundle
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.configurationStore.saveSettings
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<ImportNewProjectAttachProcessor>()

/**
 * Attaches a directory that has no `.idea` yet: opens it as a scaffold project, runs
 * [PlatformProjectOpenProcessor.runDirectoryProjectConfigurators] so language-specific configurators
 * (e.g., for `pyproject.toml`, `package.json`) can create a module, then reads the resulting module
 * back from that project's [ModuleManager] and loads its `.iml` into the target project.
 */
@ApiStatus.Internal
@InternalIgnoreDependencyViolation
class ImportNewProjectAttachProcessor : ProjectAttachProcessor() {
  override fun isEnabled(project: Project?, projectDir: Path?, newProject: Project?): Boolean {
    val dotIdeaDir = projectDir?.resolve(Project.DIRECTORY_STORE_FOLDER) ?: return false
    return Files.notExists(dotIdeaDir)
  }

  override suspend fun attachToProjectAsync(project: Project,
                                            projectDir: Path,
                                            callback: ProjectOpenedCallback?,
                                            beforeOpen: (suspend (Project) -> Boolean)?): Boolean {
    if (Files.exists(projectDir.resolve(Project.DIRECTORY_STORE_FOLDER))) {
      return false
    }

    LOG.info("Importing directory as a new project: $projectDir")
    val options = OpenProjectTask {
      useDefaultProjectAsTemplate = true
      isNewProject = true
    }
    val scaffoldProject = ProjectManagerEx.getInstanceEx().newProjectAsync(file = projectDir, options = options)
    val imlFile: Path? = try {
      PlatformProjectOpenProcessor.runDirectoryProjectConfigurators(
        projectFile = projectDir,
        project = scaffoldProject,
        newProject = true,
        createModule = true,
      )
      runInAutoSaveDisabledMode {
        saveSettings(scaffoldProject)
      }
      readAction { ModuleManager.getInstance(scaffoldProject).modules.firstOrNull()?.moduleNioFile }
    }
    finally {
      withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().runWriteAction { Disposer.dispose(scaffoldProject) }
      }
    }

    if (imlFile == null) {
      return false
    }

    val newModule: Module = try {
      attachModule(project, imlFile)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
      withContext(Dispatchers.EDT) {
        Messages.showErrorDialog(project,
                                 LangBundle.message("module.attach.dialog.message.cannot.attach.project", e.message),
                                 CommonBundle.getErrorTitle())
      }
      return false
    }

    LifecycleUsageTriggerCollector.onProjectModuleAttached(project)
    withContext(Dispatchers.EDT) {
      callback?.projectOpened(project, newModule)
    }
    return true
  }
}