// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeHighlighting.Pass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.TextEditorHighlightingPassRegistrarImpl
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Internal

class DeclarativeInlayHintsPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar, DumbAware {
  @Suppress("CompanionObjectInExtension") // used in third party
  companion object {
    @RequiresReadLock
    fun createPassForPreview(
      file: PsiFile,
      editor: Editor,
      provider: InlayHintsProvider,
      providerId: String,
      optionsToEnabled: Map<String, Boolean>,
      isDisabled: Boolean,
    ): DeclarativeInlayHintsPass {
      return DeclarativeInlayHintsPass(file, editor, listOf(InlayProviderPassInfo(provider, providerId, optionsToEnabled)), isPreview = true, isProviderDisabled = isDisabled)
    }

    fun getSuitableToFileProviders(file: PsiFile): List<InlayProviderInfo> {
      val infos = InlayHintsProviderFactory.getProvidersForLanguage(file.language)
      if (!DumbService.isDumb(file.project)) {
        return infos
      }

      return infos.filter { DumbService.isDumbAware(it.provider) }
    }

    private val PSI_MODIFICATION_STAMP: Key<Long> = Key<Long>("declarative.inlays.psi.modification.stamp")

    fun updateModificationStamp(editor: Editor, file: PsiFile) {
      updateModificationStamp(editor, file.project)
    }

    fun scheduleRecompute(editor: Editor, project: Project) {
      resetModificationStamp(editor)
      DaemonCodeAnalyzerEx.getInstanceEx(project).restart("DeclarativeInlayHintsPassFactory.scheduleRecompute")
    }

    internal fun updateModificationStamp(editor: Editor, project: Project) {
      editor.putUserData(PSI_MODIFICATION_STAMP, getCurrentModificationCount(project))
    }

    @Internal
    fun resetModificationStamp() {
      for (editor in EditorFactory.getInstance().allEditors) {
        resetModificationStamp(editor)
      }
    }

    internal fun resetModificationStamp(editor: Editor) {
      editor.putUserData(PSI_MODIFICATION_STAMP, null)
    }

    private fun getCurrentModificationCount(project: Project): Long {
      return PsiModificationTracker.getInstance(project).modificationCount
    }
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): DeclarativeInlayHintsPass? {
    if (!Registry.`is`("inlays.declarative.hints")) return null
    if (editor.isOneLineMode) return null
    if (!HighlightingLevelManager.getInstance(file.project).shouldHighlight(file)) return null

    val stamp = editor.getUserData(PSI_MODIFICATION_STAMP)
    val current = getCurrentModificationCount(file.project)
    if (current == stamp) {
      return null
    }

    val declarativeInlayHintsSettings = DeclarativeInlayHintsSettings.getInstance()
    val enabledGlobally = InlayHintsSettings.instance().hintsEnabledGlobally()
    val passProviders = if (enabledGlobally) {
      getSuitableToFileProviders(file)
        .filter {
          declarativeInlayHintsSettings.isProviderEnabled(it.providerId) ?: it.isEnabledByDefault
        }
        .map {
          val optionsToEnabled = HashMap<String, Boolean>()
          for (optionInfo in it.options) {
            val isOptionEnabled = declarativeInlayHintsSettings.isOptionEnabled(optionInfo.id, it.providerId) ?: optionInfo.isEnabledByDefault
            require(optionsToEnabled.put(optionInfo.id, isOptionEnabled) == null)
          }
          InlayProviderPassInfo(
            provider = it.provider,
            providerId = it.providerId,
            optionToEnabled = optionsToEnabled
          )
        }
    } else {
      emptyList()
    }
    return DeclarativeInlayHintsPass(file, editor, passProviders, false)
  }

  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    val ghl = intArrayOf(Pass.UPDATE_ALL).takeIf { (registrar as TextEditorHighlightingPassRegistrarImpl).isSerializeCodeInsightPasses }
    registrar.registerTextEditorHighlightingPass(this, ghl, null, false, -1)
  }
}