// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@Suppress("PATH_TO_FILE")
@Internal
suspend fun navigateFileHyperlink(
  project: Project,
  descriptor: OpenFileDescriptor,
  useBrowser: Boolean,
  requestFocus: Boolean = true,
): Boolean {
  if (project.isDisposed) {
    return false
  }

  val file = descriptor.file.takeIf { it.isValid } ?: return false
  val options = NavigationOptions.defaultOptions().requestFocus(requestFocus)
  val navigationService = project.serviceAsync<NavigationService>()
  if (file.isDirectory) {
    val resolvedDirectory = readAction {
      resolveDirectoryNavigation(project, file)
    }
    if (resolvedDirectory.directory != null && resolvedDirectory.isInProject) {
      if (resolvedDirectory.request != null) {
        navigationService.navigate(resolvedDirectory.request, options)
        return true
      }
      return withContext(Dispatchers.EDT) {
        PsiNavigationSupport.getInstance().navigateToDirectory(resolvedDirectory.directory, requestFocus)
        true
      }
    }
    PsiNavigationSupport.getInstance().openDirectoryInSystemFileManager(Path.of(file.path))
    return true
  }

  if (navigationService.navigate(descriptor, options)) {
    return true
  }
  return withContext(Dispatchers.EDT) {
    navigateFileHyperlinkLegacy(project, descriptor, useBrowser, requestFocus)
  }
}

@Internal
fun navigateFileHyperlinkLegacy(
  project: Project,
  descriptor: OpenFileDescriptor,
  useBrowser: Boolean,
  requestFocus: Boolean = true,
): Boolean {
  if (project.isDisposed) {
    return false
  }

  val file = descriptor.file.takeIf { it.isValid } ?: return false
  if (file.isDirectory) {
    val resolvedDirectory = runReadActionBlocking {
      resolveDirectoryNavigation(project, file)
    }
    if (resolvedDirectory.directory != null && resolvedDirectory.isInProject) {
      resolvedDirectory.directory.navigate(requestFocus)
    }
    else {
      PsiNavigationSupport.getInstance().openDirectoryInSystemFileManager(Path.of(file.path))
    }
    return true
  }

  val editors = FileEditorManager.getInstance(project).openEditor(descriptor, requestFocus)
  if (editors.isEmpty() && useBrowser) {
    BrowserHyperlinkInfo(file.url).navigate(project)
  }
  return true
}

private fun resolveDirectoryNavigation(project: Project, file: VirtualFile): ResolvedDirectoryNavigation {
  val psiManager = PsiManager.getInstance(project)
  val directory = psiManager.findDirectory(file)
  val isInProject = directory != null && psiManager.isInProject(directory)
  return ResolvedDirectoryNavigation(
    directory = directory,
    request = if (isInProject) NavigationRequest.directoryNavigationRequest(directory) else null,
    isInProject = isInProject,
  )
}

private data class ResolvedDirectoryNavigation(
  val directory: PsiDirectory?,
  val request: NavigationRequest?,
  val isInProject: Boolean,
)
