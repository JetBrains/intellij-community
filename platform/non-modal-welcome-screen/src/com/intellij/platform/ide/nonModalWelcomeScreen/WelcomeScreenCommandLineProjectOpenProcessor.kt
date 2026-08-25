// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.configurationStore.ProjectStorePathManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.openapi.wm.ex.getWelcomeScreenProjectProvider
import com.intellij.platform.CommandLineProjectOpenProcessor
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenPreventWelcomeTabFocusService
import com.intellij.projectImport.ProjectOpenProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * A dummy implementation of ProjectOpenProcessor that serves as a bridge for CommandLineProjectOpenProcessor.
 *
 * This class exists because the platform code searches for CommandLineProjectOpenProcessor implementations
 * among ProjectOpenProcessor extensions (see CommandLineProjectOpenProcessor.getInstanceIfExists()).
 * Although these are conceptually independent interfaces, CommandLineProjectOpenProcessor must be registered
 * as a ProjectOpenProcessor extension point to be discoverable by the platform.
 *
 * The actual functionality is provided through the CommandLineProjectOpenProcessor interface methods.
 */
internal abstract class DummyProjectOpenProcessor(override val name: String) : ProjectOpenProcessor(), CommandLineProjectOpenProcessor {
  override fun canOpenProject(file: VirtualFile): Boolean = false

  /**
   * Throws UnsupportedOperationException as this method should never be called.
   * The contract is established by canOpenProject() always returning false.
   * @see canOpenProject
   */
  override suspend fun openProjectAsync(virtualFile: VirtualFile, projectOpenOptions: ProjectOpenOptions): Project? =
    throw UnsupportedOperationException()
}

internal class WelcomeScreenCommandLineProjectOpenProcessor(
  private val getOpenProjects: () -> Array<Project> = { ProjectManager.getInstance().openProjects },
  private val getFocusedProject: () -> Project? = { IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project },
  private val createWelcomeScreenProject: suspend (WelcomeScreenProjectProvider) -> Project = {
    WelcomeScreenProjectProvider.createOrOpenWelcomeScreenProject(it)
  },
) : DummyProjectOpenProcessor("WelcomeScreenCommandLineProjectOpenProcessor") {
  private val projectOpeningMutex = Mutex()

  override suspend fun openProjectAndFile(file: Path, tempProject: Boolean, options: OpenProjectTask): Project? {
    if (tempProject) {
      return null
    }

    val provider = getWelcomeScreenProjectProvider() ?: return null
    if (!provider.canOpenFilesFromSystemFileManager(file)) {
      return null
    }
    val shouldPreferExistingProject = !provider.shouldOpenInWelcomeScreenIfFileBelongsToProject(file)
    if (shouldPreferExistingProject && fileBelongsToExistingProject(file)) {
      // If the file already belongs to an existing project,
      // fallback to default behavior,
      // do not open welcome screen project
      return null
    }
    getFocusedProject()
      ?.takeIf(::isNormalProject)
      ?.let { return openFileInProject(file, options, it) }
    return openWelcomeScreenProject(file, options, provider)
  }

  private fun isNormalProject(project: Project): Boolean {
    return !project.isDisposed &&
           !project.isDefault &&
           !LightEdit.owns(project) &&
           !WelcomeScreenProjectProvider.isWelcomeScreenProject(project)
  }

  private suspend fun fileBelongsToExistingProject(file: Path): Boolean {
    val storePathManager = serviceAsync<ProjectStorePathManager>()
    var candidate = file.parent
    while (candidate != null) {
      if (storePathManager.testStoreDirectoryExistsForProjectRoot(candidate)) return true
      candidate = candidate.parent
    }
    return false
  }

  private suspend fun openWelcomeScreenProject(
    file: Path,
    options: OpenProjectTask,
    provider: WelcomeScreenProjectProvider,
  ): Project {
    val project = getOrCreateWelcomeScreenProject(provider)
    project.serviceAsync<WelcomeScreenPreventWelcomeTabFocusService>().preventFocusOnWelcomeTab()
    return openFileInProject(file, options, project, selectInProjectView = true)
  }

  private suspend fun openFileInProject(
    file: Path,
    options: OpenProjectTask,
    project: Project,
    selectInProjectView: Boolean = false,
  ): Project {
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: return project

    focusOnFile(project, virtualFile, options.line, options.column, selectInProjectView)
    NonProjectFileWritingAccessProvider.allowWriting(listOf(virtualFile))
    return project
  }

  private suspend fun getOrCreateWelcomeScreenProject(provider: WelcomeScreenProjectProvider): Project {
    return projectOpeningMutex.withLock {
      getOpenProjects().firstOrNull(WelcomeScreenProjectProvider::isWelcomeScreenProject)
      ?: createWelcomeScreenProject(provider)
    }
  }

  private suspend fun focusOnFile(
    project: Project,
    virtualFile: VirtualFile,
    line: Int,
    column: Int,
    selectInProjectView: Boolean,
  ) {
    val fileEditorManager = project.serviceAsync<FileEditorManager>()
    withContext(Dispatchers.EDT) {
      if (line > 0) {
        OpenFileDescriptor(project, virtualFile, line - 1, column.coerceAtLeast(0)).navigate(true)
      }
      else {
        fileEditorManager.openFile(virtualFile, true)
      }
      if (selectInProjectView) {
        ProjectView.getInstance(project).select(null, virtualFile, true)
      }
    }
  }
}
