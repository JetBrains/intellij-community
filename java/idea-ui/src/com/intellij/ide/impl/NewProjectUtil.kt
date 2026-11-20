// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.featureStatistics.fusCollectors.WslUsagesCollector
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.ide.wizard.impl.NewProjectCreationHooks
import com.intellij.ide.wizard.impl.createProjectFromBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.AppUIUtil
import com.intellij.ui.IdeUICustomization
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path

/**
 * Shows [wizard] and creates its project. The new project attaches to [attachTo] with no question about the window,
 * see [OpenProjectTask.projectToAttach]. Without [attachTo], the platform asks the user where the new project opens.
 * An external plugin calls the signature without [attachTo]. Keep it binary compatible.
 */
@Suppress("DuplicatedCode")
@Internal
@JvmOverloads
suspend fun createNewProjectAsync(wizard: AbstractProjectWizard, attachTo: Project? = null) {
  // warm-up components
  serviceAsync<ProjectManager>().defaultProject

  val context = wizard.wizardContext
  val time = System.nanoTime()
  NewProjectWizardCollector.logOpen(context)
  if (!withContext(Dispatchers.EDT) { wizard.showAndGet () }) {
    NewProjectWizardCollector.logFinish(context, false, TimeoutUtil.getDurationMillis(time))
    return
  }

  try {
    val projectFile = Path.of(wizard.newProjectFilePath)
    WslUsagesCollector.beforeProjectCreated(projectFile)
    val newProject = createProjectFromWizardImpl(wizard = wizard, projectFile = projectFile, projectToClose = null, attachTo = attachTo)
    NewProjectWizardCollector.logProjectCreated(newProject, wizard.wizardContext)
  }
  catch (e: IOException) {
    AppUIUtil.invokeOnEdt { Messages.showErrorDialog(e.message, JavaUiBundle.message("dialog.title.project.initialization.failed")) }
    return
  }
  NewProjectWizardCollector.logFinish(context, true, TimeoutUtil.getDurationMillis(time))
}

object NewProjectUtil {
  @JvmStatic
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link #createNewProject(AbstractProjectWizard)}, projectToClose param is not used.",
              ReplaceWith("createNewProject(wizard)", "com.intellij.ide.impl.NewProjectUtil.createNewProject"))
  fun createNewProject(@Suppress("unused") projectToClose: Project?, wizard: AbstractProjectWizard) {
    @Suppress("DEPRECATION")
    createNewProject(wizard)
  }

  @Suppress("DuplicatedCode")
  @JvmStatic
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link #createNewProjectAsync(AbstractProjectWizard, Project)}")
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
      val projectFile = Path.of(wizard.newProjectFilePath)
      val newProject = runWithModalProgressBlocking(
        owner = ModalTaskOwner.guess(),
        title = IdeUICustomization.getInstance().projectMessage("progress.title.project.loading.name", projectFile.fileName.toString()),
      ) {
        createProjectFromWizardImpl(wizard = wizard, projectFile = projectFile, projectToClose = projectToClose)
      }
      NewProjectWizardCollector.logProjectCreated(newProject, wizard.wizardContext)
      return newProject
    }
    catch (e: IOException) {
      AppUIUtil.invokeOnEdt { Messages.showErrorDialog(e.message, JavaUiBundle.message("dialog.title.project.initialization.failed")) }
      return null
    }
  }

  fun setCompilerOutputPath(project: Project, path: String) {
    val extension = CompilerProjectExtension.getInstance(project) ?: return
    CommandProcessor.getInstance().executeCommand(project, {
      val canonicalPath = try {
        FileUtil.resolveShortWindowsName(path)
      }
      catch (_: IOException) {
        path
      }

      ApplicationManager.getApplication().runWriteAction {
        extension.compilerOutputUrl = VfsUtilCore.pathToUrl(canonicalPath)
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

@VisibleForTesting
@Internal
suspend fun createProjectFromWizardImpl(
  wizard: AbstractProjectWizard,
  projectFile: Path,
  projectToClose: Project?,
  attachTo: Project? = null,
): Project? {
  val projectBuilder = wizard.projectBuilder
  return createProjectFromBuilder(
    projectBuilder = projectBuilder,
    projectName = wizard.projectName,
    projectFile = projectFile,
    storageScheme = wizard.storageScheme,
    projectToClose = projectToClose,
    attachTo = attachTo,
    hooks = object : NewProjectCreationHooks {
      override suspend fun beforeCommit(project: Project) {
        val compileOutput = wizard.newCompileOutput
        val javaCompilerExtension = CompilerProjectExtension.getInstance(project) ?: return
        val canonicalPath = try {
          FileUtil.resolveShortWindowsName(compileOutput)
        }
        catch (_: IOException) {
          compileOutput
        }
        backgroundWriteAction {
          javaCompilerExtension.compilerOutputUrl = VfsUtilCore.pathToUrl(canonicalPath)
        }
      }

      override suspend fun afterCommit(project: Project) {
        val jdk = wizard.newProjectJdk ?: return
        backgroundWriteAction {
          JavaSdkUtil.applyJdkToProject(project, jdk)
        }
      }

      override fun afterOpened(project: Project) {
        if (projectBuilder == null || projectBuilder.isOpenProjectSettingsAfter) {
          ModulesConfigurator.showDialog(project, null, null)
        }
      }
    },
  )
}
