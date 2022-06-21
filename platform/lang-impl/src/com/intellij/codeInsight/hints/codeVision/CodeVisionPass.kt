// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.ProjectCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.concurrency.JobLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Processor
import com.jetbrains.rd.util.reactive.adviseUntil
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import kotlin.streams.toList

/**
 * Prepares data for [com.intellij.codeInsight.codeVision.CodeVisionHost].
 *
 * Doesn't actually apply result to the editor - just caches the result and notifies CodeVisionHost that
 * particular [com.intellij.codeInsight.codeVision.CodeVisionProvider] has to be invalidated.
 * Host relaunches it and takes the result of this pass from cache.
 */
class CodeVisionPass(
  rootElement: PsiElement,
  private val editor: Editor
) : EditorBoundHighlightingPass(editor, rootElement.containingFile, true) {
  companion object {
    @JvmStatic
    @Internal
    fun collectData(editor: Editor, file: PsiFile, providers: List<DaemonBoundCodeVisionProvider>) : CodeVisionData {
      val providerIdToLenses = ConcurrentHashMap<String, DaemonBoundCodeVisionCacheService.CodeVisionWithStamp>()
      collect(EmptyProgressIndicator(), editor, file, providerIdToLenses, providers)
      val allProviders = CodeVisionProviderFactory.createAllProviders(file.project)
      val dataForAllProviders = HashMap<String, DaemonBoundCodeVisionCacheService.CodeVisionWithStamp>()
      val modificationStamp = file.modificationStamp
      for (provider in allProviders) {
        if (provider !is CodeVisionProviderAdapter) continue
        val providerId = provider.id
        dataForAllProviders[providerId] = providerIdToLenses[providerId]
                                          ?: DaemonBoundCodeVisionCacheService.CodeVisionWithStamp(emptyList(), modificationStamp)
      }
      return CodeVisionData(dataForAllProviders)
    }

    private fun collect(progress: ProgressIndicator,
                        editor: Editor,
                        file: PsiFile,
                        providerIdToLenses: ConcurrentHashMap<String, DaemonBoundCodeVisionCacheService.CodeVisionWithStamp>,
                        providers: List<DaemonBoundCodeVisionProvider>) {
      val modificationTracker = PsiModificationTracker.SERVICE.getInstance(editor.project)
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(providers, progress, Processor { provider ->
        val results = provider.computeForEditor(editor, file)
        providerIdToLenses[provider.id] = DaemonBoundCodeVisionCacheService.CodeVisionWithStamp(results,
                                                                                                modificationTracker.modificationCount)
        true
      })
    }

    internal fun updateProviders(project: Project,
                                 editor: Editor,
                                 providerIdToLenses: Map<String, DaemonBoundCodeVisionCacheService.CodeVisionWithStamp>) {
      val codeVisionHost = CodeVisionHost.getInstance(project)
      codeVisionHost.invalidateProviderSignal.fire(CodeVisionHost.LensInvalidateSignal(editor, providerIdToLenses.keys))
    }

    internal fun saveToCache(project: Project,
                             editor: Editor,
                             providerIdToLenses: Map<String, DaemonBoundCodeVisionCacheService.CodeVisionWithStamp>) {
      val cacheService = DaemonBoundCodeVisionCacheService.getInstance(project)
      for ((providerId, results) in providerIdToLenses) {
        cacheService.storeVisionDataForEditor(editor, providerId, results)
      }
    }
  }

  private val providerIdToLenses: ConcurrentHashMap<String, DaemonBoundCodeVisionCacheService.CodeVisionWithStamp> = ConcurrentHashMap()
  private val currentIndicator = ProgressManager.getGlobalProgressIndicator()

  override fun doCollectInformation(progress: ProgressIndicator) {
    val settings = CodeVisionSettings.instance()
    val providers = DaemonBoundCodeVisionProvider.extensionPoint.extensions()
      .filter {  settings.isProviderEnabled(it.groupId) }
      .collect(Collectors.toList())
    collect(progress, editor, myFile, providerIdToLenses, providers)
  }

  override fun doApplyInformationToEditor() {
    saveToCache(myProject, editor, providerIdToLenses)
    val lensPopupActive = ProjectCodeVisionModel.getInstance(editor.project!!).lensPopupActive
    if (lensPopupActive.value.not()) {
      updateProviders(myProject, editor, providerIdToLenses)
    }
    else {
      lensPopupActive.adviseUntil(myProject.createLifetime()) {
        if (it) return@adviseUntil false
        if (currentIndicator == null || currentIndicator.isCanceled) return@adviseUntil true
        updateProviders(myProject, editor, providerIdToLenses)
        true
      }
    }
  }

  class CodeVisionData internal constructor(
    private val providerIdToLenses: Map<String, DaemonBoundCodeVisionCacheService.CodeVisionWithStamp>
  ) {
    fun applyTo(editor: Editor, project: Project) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      saveToCache(project, editor, providerIdToLenses)
      updateProviders(project, editor, providerIdToLenses)
    }

    override fun toString(): String {
      return providerIdToLenses.toString()
    }
  }
}