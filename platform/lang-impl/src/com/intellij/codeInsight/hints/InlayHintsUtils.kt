// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.configurationStore.deserializeInto
import com.intellij.configurationStore.serialize
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import java.awt.Dimension
import java.awt.Rectangle

class ProviderWithSettings<T: Any>(
  val info: ProviderInfo<T>,
  var settings: T
) {
  val configurable by lazy { provider.createConfigurable(settings) }

  val provider: InlayHintsProvider<T>
  get() = info.provider
  val language: Language
  get() = info.language
}

fun <T : Any> ProviderWithSettings<T>.withSettingsCopy(): ProviderWithSettings<T> {
  val settingsCopy = copySettings(settings, provider)
  return ProviderWithSettings(info, settingsCopy)
}

fun <T : Any> ProviderWithSettings<T>.getCollectorWrapperFor(file: PsiFile, editor: Editor, language: Language): CollectorWithSettings<T>? {
  val key = provider.key
  val sink = InlayHintsSinkImpl(key)
  val collector = provider.getCollectorFor(file, editor, settings, sink) ?: return null
  return CollectorWithSettings(collector, key, language, sink)
}

internal fun <T : Any> InlayHintsProvider<T>.withSettings(language: Language, config: InlayHintsSettings): ProviderWithSettings<T> {
  val settings = getActualSettings(config, language)
  return ProviderWithSettings(ProviderInfo(language, this), settings)
}

internal fun <T : Any> InlayHintsProvider<T>.getActualSettings(config: InlayHintsSettings, language: Language): T =
  config.findSettings(key, language) { createSettings() }

internal fun <T: Any> copySettings(from: T, provider: InlayHintsProvider<T>): T {
  val settings = provider.createSettings()
  // Workaround to make a deep copy of settings. The other way is to parametrize T with something like
  // interface DeepCopyable<T> { fun deepCopy(from: T): T }, but there will be a lot of problems with recursive type bounds
  // That way was implemented and rejected
  serialize(from)?.deserializeInto(settings)
  return settings
}

class CollectorWithSettings<T : Any>(
  val collector: InlayHintsCollector,
  val key: SettingsKey<T>,
  val language: Language,
  val sink: InlayHintsSinkImpl<T>
) {
  fun collectHints(element: PsiElement, editor: Editor): Boolean {
    return collector.collect(element, editor, sink)
  }

  fun applyToEditor(
    editor: Editor,
    existingHorizontalInlays: MarkList<Inlay<*>>,
    existingVerticalInlays: MarkList<Inlay<*>>,
    isEnabled: Boolean
  ) {
    sink.applyToEditor(editor, existingHorizontalInlays, existingVerticalInlays, isEnabled)
  }

  fun collectTraversingAndApply(editor: Editor, file: PsiFile) {
    val traverser = SyntaxTraverser.psiTraverser(file)
    traverser.forEach {
      collectHints(it, editor)
    }
    val model = editor.inlayModel
    val startOffset = file.textOffset
    val endOffset = file.textRange.endOffset
    val existingHorizontalInlays: MarkList<Inlay<*>> = MarkList(model.getInlineElementsInRange(startOffset, endOffset))
    val existingVerticalInlays: MarkList<Inlay<*>> = MarkList(model.getBlockElementsInRange(startOffset, endOffset))
    applyToEditor(editor, existingHorizontalInlays, existingVerticalInlays, true)
  }
}

fun InlayPresentation.fireContentChanged() {
  fireContentChanged(Rectangle(width, height))
}

fun InlayPresentation.fireUpdateEvent(previousDimension: Dimension) {
  val current = dimension()
  if (previousDimension != current) {
    fireSizeChanged(previousDimension, current)
  }
  fireContentChanged()
}

fun InlayPresentation.dimension() = Dimension(width, height)