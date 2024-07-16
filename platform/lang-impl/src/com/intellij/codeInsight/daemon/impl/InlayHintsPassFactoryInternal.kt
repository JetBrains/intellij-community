// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.*
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorInitializer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus


private val PSI_MODIFICATION_STAMP: Key<Long> = Key.create("inlay.psi.modification.stamp")
private val ALWAYS_ENABLED_HINTS_PROVIDERS: Key<Set<SettingsKey<*>>> = Key.create("inlay.hints.always.enabled.providers")

class InlayHintsPassFactoryInternal : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar, DumbAware {
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    val ghl = intArrayOf(Pass.UPDATE_ALL).takeIf { (registrar as TextEditorHighlightingPassRegistrarImpl).isSerializeCodeInsightPasses }
    registrar.registerTextEditorHighlightingPass(this, ghl, null, false, -1)
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (editor.isOneLineMode) return null
    val savedStamp = editor.getUserData(PSI_MODIFICATION_STAMP)
    if (DiffUtil.isDiffEditor(editor)) return null
    val currentStamp = getCurrentModificationStamp(file)
    if (savedStamp != null && savedStamp == currentStamp) return null

    val language = file.language
    val hintSink = InlayHintsSinkImpl(editor)
    val collectors = getProviders(file, editor).mapNotNull { it.getCollectorWrapperFor(file, editor, language, hintSink) }
    val priorityRange = HighlightingSessionImpl.getFromCurrentIndicator(file).visibleRange

    return InlayHintsPass(file, collectors, editor, priorityRange, hintSink)
  }

  @Suppress("CompanionObjectInExtension") // used in external plugins (https://youtrack.jetbrains.com/issue/IDEA-333164/Breaking-change-with-InlayHintsPassFactory-class-moved-in-another-package)
  companion object {
    fun forceHintsUpdateOnNextPass() {
      for (editor in EditorFactory.getInstance().allEditors) {
        clearModificationStamp(editor)
      }
    }

    fun restartDaemonUpdatingHints(project: Project) {
      forceHintsUpdateOnNextPass()
      DaemonCodeAnalyzer.getInstance(project).restart()
    }

    fun putCurrentModificationStamp(editor: Editor, file: PsiFile) {
      editor.putUserData(PSI_MODIFICATION_STAMP, getCurrentModificationStamp(file))
    }

    fun clearModificationStamp(editor: Editor): Unit = editor.putUserData(PSI_MODIFICATION_STAMP, null)

    private fun getCurrentModificationStamp(file: PsiFile): Long {
      return file.manager.modificationTracker.modificationCount
    }
  }
}

private fun isProviderAlwaysEnabledForEditor(editor: Editor, providerKey: SettingsKey<*>): Boolean {
  val alwaysEnabledProviderKeys = editor.getUserData(ALWAYS_ENABLED_HINTS_PROVIDERS) ?: return false
  return providerKey in alwaysEnabledProviderKeys
}

private fun collectPlaceholders(file: PsiFile, editor: Editor): HintsBuffer? {
  val collector = getProviders(file, editor).firstNotNullOfOrNull { it.getPlaceholderCollectorFor(file, editor) }
  return collector?.collectTraversing(editor = editor, file = file, enabled = true)
}

private fun getProviders(element: PsiElement, editor: Editor): List<ProviderWithSettings<*>> {
  val settings = InlayHintsSettings.instance()
  val language = element.language

  val project = element.project

  return HintUtils.getHintProvidersForLanguage(language).filter {
    DumbService.getInstance(project).isUsableInCurrentContext(it.provider) &&
    // to avoid cases when old and new code vision UI are shown
    !(it.provider.group == InlayGroup.CODE_VISION_GROUP && Registry.`is`("editor.codeVision.new")) &&
    (settings.hintsShouldBeShown(it.provider.key, language) || isProviderAlwaysEnabledForEditor(editor, it.provider.key))
  }
}

private class InlayHintsEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(project: Project,
                                        file: VirtualFile,
                                        document: Document,
                                        editorSupplier: suspend () -> EditorEx,
                                        highlighterReady: suspend () -> Unit) {
    val editor = editorSupplier.invoke()

    var buffer: HintsBuffer? = null
    var psiFile: PsiFile? = null

    val psiManager = project.serviceAsync<PsiManager>()
    readActionBlocking {
      psiFile = psiManager.findFile(file)
      psiFile?.let {
        buffer = collectPlaceholders(file = it, editor = editor)
      }
    }

    @ApiStatus.Internal
    @RequiresEdt
    fun applyPlaceholders(file: PsiFile, editor: Editor, hints: HintsBuffer): Unit = InlayHintsPass.applyCollected(hints, file, editor,
                                                                                                                   true)
  }
}
