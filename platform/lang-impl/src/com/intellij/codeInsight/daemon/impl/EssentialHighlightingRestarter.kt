// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.EssentialHighlightingRestarterDisablement
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.SaveAndSyncHandlerListener
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.impl.PsiManagerEx

private val EP_NAME = create<EssentialHighlightingRestarterDisablement>("com.intellij.daemon.essentialHighlightingRestarterDisablement")

private fun isEssentialHighlightingRestarterDisabledForProject(project: Project): Boolean {
  return EP_NAME.extensionList.any { it.shouldBeDisabledForProject(project) }
}

/**
 * Tells [DaemonCodeAnalyzerImpl] to run full set of passes after "Save all" action was invoked, to show all diagnostics
 * if the current selected file configured as "Highlight: Essential only"
 */
private class EssentialHighlightingRestarter(private val project: Project) : SaveAndSyncHandlerListener {
  override suspend fun beforeSave(task: SaveAndSyncHandler.SaveTask, forceExecuteImmediately: Boolean) {
    if (!project.isInitialized() || project.isDisposed()
        || !Registry.`is`("highlighting.essential.should.restart.in.full.mode.on.save.all") || isEssentialHighlightingRestarterDisabledForProject(
        project)
    ) {
      return
    }
    val hasFilesWithEssentialHighlightingConfigured = FileEditorManager.getInstance(project).getOpenFiles()
      .mapNotNull { vf ->
        readAction { PsiManagerEx.getInstanceEx(project).getFileManager().findFile(vf!!) }
      }
      .any { psiFile ->
        readAction {
          HighlightingSettingsPerFile.getInstance(project).getHighlightingSettingForRoot(psiFile) ==
            FileHighlightingSetting.ESSENTIAL
        }
      }
    if (hasFilesWithEssentialHighlightingConfigured) {
      val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
      codeAnalyzer.requestRestartToCompleteEssentialHighlighting()
    }
  }
}
