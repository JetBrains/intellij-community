// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.EssentialHighlightingRestarterDisablement
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.SaveAndSyncHandlerListener
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.serviceContainer.AlreadyDisposedException

private val EP_NAME = create<EssentialHighlightingRestarterDisablement>("com.intellij.daemon.essentialHighlightingRestarterDisablement")

private fun isEssentialHighlightingRestarterDisabledForProject(project: Project): Boolean {
  return EP_NAME.extensionList.any { it.shouldBeDisabledForProject(project) }
}

/**
 * Tells [DaemonCodeAnalyzerImpl] to run a full set of passes after "Save all" action was invoked, to show all diagnostics
 * if the current selected file configured as "Highlight: Essential only"
 */
private class EssentialHighlightingRestarter() : SaveAndSyncHandlerListener {
  @Suppress("IncorrectCancellationExceptionHandling")
  override suspend fun beforeSave(task: SaveAndSyncHandler.SaveTask, forceExecuteImmediately: Boolean) {
    val requestedProject = task.project
    if (requestedProject == null) {
      for (project in getOpenedProjects()) {
        try {
          requestRestartToCompleteEssentialHighlighting(project)
        }
        catch (_: AlreadyDisposedException) {
        }
      }
    }
    else {
      requestRestartToCompleteEssentialHighlighting(requestedProject)
    }
  }
}

private suspend fun requestRestartToCompleteEssentialHighlighting(project: Project) {
  if (!project.isInitialized() ||
      !Registry.`is`("highlighting.essential.should.restart.in.full.mode.on.save.all", true) ||
      isEssentialHighlightingRestarterDisabledForProject(project)) {
    return
  }

  val openFiles = project.serviceIfCreated<FileEditorManager>()?.getOpenFiles()
  if (openFiles.isNullOrEmpty()) {
    return
  }

  val psiFileManager = (project.serviceAsync<PsiManager>() as PsiManagerEx).getFileManager()
  val highlightingSettingsPerFile = project.serviceAsync<HighlightingLevelManager>() as HighlightingSettingsPerFile
  val hasFilesWithEssentialHighlightingConfigured = openFiles
    .any { file ->
      readAction {
        val psiFile = psiFileManager.findFile(file) ?: return@readAction false
        highlightingSettingsPerFile.getHighlightingSettingForRoot(psiFile) == FileHighlightingSetting.ESSENTIAL
      }
    }
  if (hasFilesWithEssentialHighlightingConfigured) {
    (project.serviceAsync<DaemonCodeAnalyzer>() as DaemonCodeAnalyzerImpl).requestRestartToCompleteEssentialHighlighting()
  }
}
