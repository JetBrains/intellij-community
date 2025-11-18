// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.executeNoProjectStateHandlerExpectingNonWelcomeScreenImplementation
import com.intellij.openapi.wm.ex.getWelcomeScreenProjectProvider
import com.intellij.platform.CommandLineProjectOpenProcessor
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenPreventWelcomeTabFocusService
import com.intellij.projectImport.ProjectOpenProcessor
import kotlinx.coroutines.Dispatchers
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
private abstract class DummyProjectOpenProcessor(override val name: String) : ProjectOpenProcessor(), CommandLineProjectOpenProcessor {
  override fun canOpenProject(file: VirtualFile): Boolean = false

  /**
   * Throws UnsupportedOperationException as this method should never be called.
   * The contract is established by canOpenProject() always returning false.
   * @see canOpenProject
   */
  override suspend fun openProjectAsync(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? =
    throw UnsupportedOperationException()
}

private class WelcomeScreenCommandLineProjectOpenProcessor : DummyProjectOpenProcessor("WelcomeScreenCommandLineProjectOpenProcessor") {
  override suspend fun openProjectAndFile(file: Path, tempProject: Boolean, options: OpenProjectTask): Project? {
    val provider = getWelcomeScreenProjectProvider() ?: return null
    return openWelcomeScreenProject(file)
  }

  private suspend fun openWelcomeScreenProject(file: Path): Project {
    FUSProjectHotStartUpMeasurer.lightEditProjectFound()
    val project = executeNoProjectStateHandlerExpectingNonWelcomeScreenImplementation()
    project.serviceAsync<WelcomeScreenPreventWelcomeTabFocusService>().preventFocusOnWelcomeTab()
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)?.let { focusOnFile(project, it) }
    return project
  }

  private suspend fun focusOnFile(project: Project, virtualFile: VirtualFile) {
    val fileEditorManager = project.serviceAsync<FileEditorManager>()
    withContext(Dispatchers.EDT) {
      fileEditorManager.openFile(virtualFile, true)
      ProjectView.getInstance(project).select(null, virtualFile, true)
    }
  }
}
