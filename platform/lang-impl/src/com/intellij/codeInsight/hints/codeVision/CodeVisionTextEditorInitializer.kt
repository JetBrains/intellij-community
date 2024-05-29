// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.lensContext
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.daemon.impl.grave.CodeVisionGrave
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorInitializer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CodeVisionTextEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(project: Project,
                                        file: VirtualFile,
                                        document: Document,
                                        editorSupplier: suspend () -> EditorEx,
                                        highlighterReady: suspend () -> Unit) {
    if (!Registry.`is`("editor.codeVision.new", true) ||
        !CodeVisionSettings.getInstance().codeVisionEnabled) {
      return
    }

    val raisedZombies = raiseZombies(project, file, document)
    if (raisedZombies.isNotEmpty()) {
      val editor = editorSupplier()
      withContext(Dispatchers.EDT) {
        editor.lensContext?.setZombieResults(raisedZombies)
      }
      return
    }

    val editor = editorSupplier.invoke()
    val psiManager = project.serviceAsync<PsiManager>()
    val psiFile = readActionBlocking { psiManager.findFile(file) }
    val placeholders = project.serviceAsync<CodeVisionHost>().collectPlaceholders(editor, psiFile)
    if (placeholders.isEmpty()) {
      return
    }

    withContext(Dispatchers.EDT) {
      editor.lensContext?.setResults(placeholders)
    }
  }

  private suspend fun raiseZombies(project: Project, file: VirtualFile, document: Document): List<Pair<TextRange, CodeVisionEntry>> {
    val grave = project.serviceAsync<CodeVisionGrave>()
    val raisedState = grave.raise(file, document)
    if (raisedState == null) {
      return emptyList()
    }
    val providerIdToGroupId = providerToGroupMap(project)
    val settings = CodeVisionSettings.getInstance()
    return raisedState.filter { (_, zombieEntry) ->
      val zombieGroup = providerIdToGroupId[zombieEntry.providerId]
      zombieGroup != null && settings.isProviderEnabled(zombieGroup)
    }
  }

  private suspend fun providerToGroupMap(project: Project): Map<String, String> {
    // Examples to remind what is what
    // groupId:    "rename", "vcs.code.vision", "references", "llm", "component.usage", "inheritors", "problems"
    // providerId: "Rename refactoring", "vcs.code.vision", "java.references", "LLMDocumentationCodeVisionProvider",
    //             "JSComponentUsageCodeVisionProvider", "go.references", "go.inheritors", "java.inheritors",
    //             "java.RelatedProblems", "python.references", "js.references", "js.inheritors",
    //             "cypress.commands.references", "php.references", "php.inheritors"
    val cvHost = project.serviceAsync<CodeVisionHost>()
    return cvHost.providers.associate { provider -> provider.id to provider.groupId }
  }
}
