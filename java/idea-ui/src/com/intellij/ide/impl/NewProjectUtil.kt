// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.impl.NewProjectUtil.createFromWizard
import com.intellij.ide.impl.NewProjectUtil.setCompilerOutputPath
import com.intellij.ide.impl.OpenProjectTask.Companion.build
import com.intellij.ide.impl.ProjectUtil.focusProjectWindow
import com.intellij.ide.impl.ProjectUtil.getOpenProjects
import com.intellij.ide.impl.ProjectUtil.isSameProject
import com.intellij.ide.impl.ProjectUtil.updateLastProjectLocation
import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.ui.AppUIUtil
import com.intellij.ui.IdeUICustomization
import com.intellij.util.TimeoutUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CancellationException

private val LOG = logger<NewProjectUtil>()

internal suspend fun createNewProjectAsync(wizard: AbstractProjectWizard) {
  // warm-up components
  serviceAsync<ProjectManager>().defaultProject

  val context = wizard.wizardContext
  val time = System.nanoTime()
  NewProjectWizardCollector.logOpen(context)
  if (wizard.showAndGet()) {
    createFromWizard(wizard)
    NewProjectWizardCollector.logFinish(context, true, TimeoutUtil.getDurationMillis(time))
  }
  else {
    NewProjectWizardCollector.logFinish(context, false, TimeoutUtil.getDurationMillis(time))
  }
}

object NewProjectUtil {
  @JvmStatic
  @Deprecated("Use {@link #createNewProject(AbstractProjectWizard)}, projectToClose param is not used.",
              ReplaceWith("createNewProject(wizard)", "com.intellij.ide.impl.NewProjectUtil.createNewProject"))
  fun createNewProject(@Suppress("unused") projectToClose: Project?, wizard: AbstractProjectWizard) {
    createNewProject(wizard)
  }

  @JvmStatic
  fun createNewProject(wizard: AbstractProjectWizard) {
    // warm-up components
    ProjectManager.getInstance().defaultProject
    val context = wizard.wizardContext
    val time = System.nanoTime()
    NewProjectWizardCollector.logOpen(context)
    if (wizard.showAndGet()) {
      createFromWizard(wizard)
      NewProjectWizardCollector.logFinish(context, true, TimeoutUtil.getDurationMillis(time))
    }
    else {
      NewProjectWizardCollector.logFinish(context, false, TimeoutUtil.getDurationMillis(time))
    }
  }

  @JvmOverloads
  @JvmStatic
  fun createFromWizard(wizard: AbstractProjectWizard, projectToClose: Project? = null): Project? {
    try {
      val newProject = doCreate(wizard, projectToClose)
      NewProjectWizardCollector.logProjectCreated(newProject, wizard.wizardContext)
      return newProject
    }
    catch (e: IOException) {
      AppUIUtil.invokeOnEdt { Messages.showErrorDialog(e.message, JavaUiBundle.message("dialog.title.project.initialization.failed")) }
      return null
    }
  }

  fun setCompilerOutputPath(project: Project, path: String) {
    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        val extension = CompilerProjectExtension.getInstance(project)
        if (extension != null) {
          val canonicalPath = try {
            FileUtil.resolveShortWindowsName(path)
          }
          catch (_: IOException) {
            path
          }
          extension.compilerOutputUrl = VfsUtilCore.pathToUrl(canonicalPath)
        }
      }
    }, null, null)
  }

  @JvmStatic
  @Deprecated("Use JavaSdkUtil.applyJdkToProject() directly ",
              ReplaceWith("JavaSdkUtil.applyJdkToProject(project, jdk)", "com.intellij.openapi.projectRoots.ex.JavaSdkUtil"))
  fun applyJdkToProject(project: Project, jdk: Sdk) {
    JavaSdkUtil.applyJdkToProject(project, jdk)
  }
}

private fun doCreate(wizard: AbstractProjectWizard, projectToClose: Project?): Project? {
  val projectFilePath = wizard.newProjectFilePath
  for (p in getOpenProjects()) {
    if (isSameProject(Paths.get(projectFilePath), p)) {
      focusProjectWindow(p, false)
      return null
    }
  }

  val projectBuilder = wizard.projectBuilder
  LOG.debug { "builder $projectBuilder" }
  val projectManager = ProjectManagerEx.getInstanceEx()
  try {
    val projectFile = Path.of(projectFilePath)
    val projectDir = if (wizard.storageScheme == StorageScheme.DEFAULT) {
      projectFile.parent ?: throw IOException("Cannot create project in '$projectFilePath': no parent file exists")
    }
    else {
      projectFile
    }
    Files.createDirectories(projectDir)
    val newProject: Project? = if (projectBuilder == null || !projectBuilder.isUpdate) {
      val name = wizard.projectName
      if (projectBuilder == null) {
        projectManager.newProject(projectFile, build().asNewProject().withProjectName(name))
      }
      else {
        try {
          projectBuilder.createProject(name, projectFilePath)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
          null
        }
      }
    }
    else {
      projectToClose
    }

    if (newProject == null) {
      return projectToClose
    }

    val compileOutput = wizard.newCompileOutput
    setCompilerOutputPath(newProject, compileOutput)
    if (projectBuilder != null) {
      // validate can require project on disk
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        newProject.save()
      }
      if (!projectBuilder.validate(projectToClose, newProject)) {
        return projectToClose
      }
      projectBuilder.commit(newProject, null, ModulesProvider.EMPTY_MODULES_PROVIDER)
    }

    val jdk = wizard.newProjectJdk
    if (jdk != null) {
      CommandProcessor.getInstance().executeCommand(newProject, {
        ApplicationManager.getApplication().runWriteAction {
          JavaSdkUtil.applyJdkToProject(newProject, jdk)
        }
      }, null, null)
    }

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      val needToOpenProjectStructure = projectBuilder == null || projectBuilder.isOpenProjectSettingsAfter
      StartupManager.getInstance(newProject).runAfterOpened {
        // ensure the dialog is shown after all startup activities are done
        ApplicationManager.getApplication().invokeLater(
          {
            if (needToOpenProjectStructure) {
              ModulesConfigurator.showDialog(newProject, null, null)
            }
            ApplicationManager.getApplication().invokeLater(
              {
                ToolWindowManager.getInstance(newProject).getToolWindow(ToolWindowId.PROJECT_VIEW)?.activate(null)
              }, ModalityState.nonModal(), newProject.disposed)
          }, ModalityState.nonModal(), newProject.disposed)
      }
    }

    if (newProject !== projectToClose) {
      updateLastProjectLocation(projectFile)
      val moduleConfigurator = projectBuilder.createModuleConfigurator()
      val options =  OpenProjectTask {
        project = newProject
        projectName = projectFile.fileName.toString()
        callback = ProjectOpenedCallback { openedProject, module ->
          if (openedProject != newProject && module != null) { // project attached
            ApplicationManager.getApplication().invokeLater {
              moduleConfigurator?.accept(module)
            }
          }
        }
      }
      runWithModalProgressBlocking(
        owner = ModalTaskOwner.guess(),
        title = IdeUICustomization.getInstance().projectMessage("progress.title.project.loading.name", options.projectName),
      ) {
        serviceAsync<TrustedPaths>().setProjectPathTrusted(projectDir, true)
        (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(projectStoreBaseDir = projectDir, options = options)
      }
    }
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      SaveAndSyncHandler.getInstance().scheduleProjectSave(newProject)
    }

    return newProject
  }
  finally {
    projectBuilder?.cleanup()
  }
}