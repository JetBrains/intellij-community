// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.ui.model.ProjectCodeVisionModel
import com.intellij.concurrency.JobLauncher
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.rd.createLifetime
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.Processor
import com.jetbrains.rd.util.reactive.adviseUntil
import java.util.concurrent.ConcurrentHashMap

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
  private val providerIdToLenses: MutableMap<String, DaemonBoundCodeVisionCacheService.CodeVisionWithStamp> = ConcurrentHashMap()
  private val currentIndicator = ProgressManager.getGlobalProgressIndicator()

  override fun doCollectInformation(progress: ProgressIndicator) {
    val providers = DaemonBoundCodeVisionProvider.extensionPoint.extensionList
    val modificationTracker = PsiModificationTracker.SERVICE.getInstance(editor.project)
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(providers, progress, Processor { provider ->
      val results = provider.computeForEditor(editor)
      providerIdToLenses[provider.id] = DaemonBoundCodeVisionCacheService.CodeVisionWithStamp(results, modificationTracker.modificationCount)
      true
    })
  }

  override fun doApplyInformationToEditor() {
    val cacheService = DaemonBoundCodeVisionCacheService.getInstance(myProject)
    for ((providerId, results) in providerIdToLenses) {
      cacheService.storeVisionDataForEditor(myEditor, providerId, results)
    }
    val lensPopupActive = ProjectCodeVisionModel.getInstance(editor.project!!).lensPopupActive
    if (lensPopupActive.value.not()) {
      updateProviders()
    }
    else {
      lensPopupActive.adviseUntil(myProject.createLifetime()) {
        if(it) return@adviseUntil false
        if (currentIndicator == null || currentIndicator.isCanceled) return@adviseUntil true
        updateProviders()
        true
      }
    }
  }

  private fun updateProviders() {
    val codeVisionHost = CodeVisionHost.getInstance(myProject)
    for (providerId in providerIdToLenses.keys) {
      codeVisionHost.invalidateProviderSignal.fire(CodeVisionHost.LensInvalidateSignal(editor, providerIdToLenses.keys))
    }
  }
}