// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.AttributesTransformerPresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.RecursivelyUpdatingRootPresentation
import com.intellij.codeInsight.hints.presentation.RootInlayPresentation
import com.intellij.configurationStore.deserializeInto
import com.intellij.configurationStore.serialize
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributesEffectsBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.SmartList
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Title
import java.awt.Dimension
import java.awt.Rectangle
import java.util.function.Supplier

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
  val sink = InlayHintsSinkImpl(editor)
  val collector = provider.getCollectorFor(file, editor, settings, sink) ?: return null
  return CollectorWithSettings(collector, key, language, sink)
}

internal fun <T : Any> ProviderWithSettings<T>.getPlaceholdersCollectorFor(file: PsiFile, editor: Editor): CollectorWithSettings<T>? {
  val key = provider.key
  val sink = InlayHintsSinkImpl(editor)
  val collector = provider.getPlaceholdersCollectorFor(file, editor, settings, sink) ?: return null

  return CollectorWithSettings(collector, key, language, sink)
}

internal fun <T : Any> InlayHintsProvider<T>.withSettings(language: Language, config: InlayHintsSettings): ProviderWithSettings<T> {
  val settings = getActualSettings(config, language)
  return ProviderWithSettings(ProviderInfo(language, this), settings)
}

internal fun <T : Any> InlayHintsProvider<T>.getActualSettings(config: InlayHintsSettings, language: Language): T =
  config.findSettings(key, language) { createSettings() }

internal fun <T : Any> copySettings(from: T, provider: InlayHintsProvider<T>): T {
  val settings = provider.createSettings()
  // Workaround to make a deep copy of settings. The other way is to parametrize T with something like
  // interface DeepCopyable<T> { fun deepCopy(from: T): T }, but there will be a lot of problems with recursive type bounds
  // That way was implemented and rejected
  serialize(from)?.deserializeInto(settings)
  return settings
}

internal fun strikeOutBuilder(editor: Editor): TextAttributesEffectsBuilder {
  val effectColor = editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLAY_DEFAULT).foregroundColor
  return TextAttributesEffectsBuilder.create().coverWith(EffectType.STRIKEOUT, effectColor)
}

class CollectorWithSettings<T : Any>(
  val collector: InlayHintsCollector,
  val key: SettingsKey<T>,
  val language: Language,
  val sink: InlayHintsSinkImpl
) {
  fun collectHints(element: PsiElement, editor: Editor): Boolean {
    return collector.collect(element, editor, sink)
  }

  /**
   * Collects hints from the file and apply them to editor.
   * Doesn't expect other hints in editor.
   * Use only for settings preview.
   */
  fun collectTraversingAndApply(editor: Editor, file: PsiFile, enabled: Boolean) {
    val hintsBuffer = collectTraversing(editor, file, enabled)
    applyToEditor(file, editor, hintsBuffer)
  }

  /**
   * Same as [collectTraversingAndApply] but invoked on bg thread
   */
  fun collectTraversingAndApplyOnEdt(editor: Editor, file: PsiFile, enabled: Boolean) {
    val hintsBuffer = collectTraversing(editor, file, true)
    if (!enabled) {
      val builder = strikeOutBuilder(editor)
      addStrikeout(hintsBuffer.inlineHints, builder) { root, constraints -> HorizontalConstrainedPresentation(root, constraints) }
      addStrikeout(hintsBuffer.blockAboveHints, builder) { root, constraints -> BlockConstrainedPresentation(root, constraints) }
      addStrikeout(hintsBuffer.blockBelowHints, builder) { root, constraints -> BlockConstrainedPresentation(root, constraints) }
    }
    invokeLater { applyToEditor(file, editor, hintsBuffer) }
  }

  fun collectTraversing(editor: Editor, file: PsiFile, enabled: Boolean): HintsBuffer {
    if (enabled) {
      val traverser = SyntaxTraverser.psiTraverser(file)
      traverser.forEach {
        collectHints(it, editor)
      }
    }
    return sink.complete()
  }

  fun applyToEditor(file: PsiFile, editor: Editor, hintsBuffer: HintsBuffer) {
    InlayHintsPass.applyCollected(hintsBuffer, file, editor)
  }
}

internal fun <T: Any> addStrikeout(inlineHints: Int2ObjectOpenHashMap<MutableList<ConstrainedPresentation<*, T>>>,
                          builder: TextAttributesEffectsBuilder,
                          factory: (RootInlayPresentation<*>, T?) -> ConstrainedPresentation<*, T>
) {
  inlineHints.forEach {
    it.value.replaceAll { presentation ->
      val transformer = AttributesTransformerPresentation(presentation.root) { builder.applyTo(it) }
      val rootPresentation = RecursivelyUpdatingRootPresentation(transformer)
      factory(rootPresentation, presentation.constraints)
    }
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

private typealias ConstrPresent<C> = ConstrainedPresentation<*, C>

@ApiStatus.Experimental
fun InlayHintsSink.addCodeVisionElement(editor: Editor, offset: Int, priority: Int, presentation: InlayPresentation) {
  val line = editor.document.getLineNumber(offset)
  val column = offset - editor.document.getLineStartOffset(line)
  val root = RecursivelyUpdatingRootPresentation(presentation)
  val constraints = BlockConstraints(false, priority, InlayGroup.CODE_VISION_GROUP.ordinal, column)

  addBlockElement(line, true, root, constraints)
}

object InlayHintsUtils {
  fun getDefaultInlayHintsProviderPopupActions(
    providerKey: SettingsKey<*>,
    providerName: Supplier<@Nls(capitalization = Title) String>
  ): List<AnAction> =
    listOf(
      DisableInlayHintsProviderAction(providerKey, providerName, false),
      ConfigureInlayHintsProviderAction(providerKey)
    )

  fun getDefaultInlayHintsProviderCasePopupActions(
    providerKey: SettingsKey<*>,
    providerName: Supplier<@Nls(capitalization = Title) String>,
    caseId: String,
    caseName: Supplier<@Nls(capitalization = Title) String>
  ): List<AnAction> =
    listOf(
      DisableInlayHintsProviderCaseAction(providerKey, providerName, caseId, caseName),
      DisableInlayHintsProviderAction(providerKey, providerName, true),
      ConfigureInlayHintsProviderAction(providerKey)
    )

  /**
   * Function updates list of old presentations with new list, taking into account priorities.
   * Both lists must be sorted.
   *
   * @return list of updated constrained presentations
   */
  fun <Constraint : Any> produceUpdatedRootList(
    new: List<ConstrPresent<Constraint>>,
    old: List<ConstrPresent<Constraint>>,
    comparator: Comparator<ConstrPresent<Constraint>>,
    editor: Editor,
    factory: InlayPresentationFactory
  ): List<ConstrPresent<Constraint>> {
    val updatedPresentations: MutableList<ConstrPresent<Constraint>> = SmartList()

    // TODO [roman.ivanov]
    //  this function creates new list anyway, even if nothing from old presentations got updated,
    //  which makes us update list of presentations on every update (which should be relatively rare!)
    //  maybe I should really create new list only in case when anything get updated
    val oldSize = old.size
    val newSize = new.size
    var oldIndex = 0
    var newIndex = 0
    // Simultaneous bypass of both lists and merging them to new one with element update
    loop@
    while (true) {
      val newEl = new[newIndex]
      val oldEl = old[oldIndex]
      val value = comparator.compare(newEl, oldEl)
      when {
        value > 0 -> {
          oldIndex++
          if (oldIndex == oldSize) {
            break@loop
          }
        }
        value < 0 -> {
          updatedPresentations.add(newEl)
          newIndex++
          if (newIndex == newSize) {
            break@loop
          }
        }
        else -> {
          val oldRoot = oldEl.root
          val newRoot = newEl.root

          if (newRoot.key == oldRoot.key) {
            oldRoot.updateIfSame(newRoot, editor, factory)
            updatedPresentations.add(oldEl)
          }
          else {
            updatedPresentations.add(newEl)
          }
          newIndex++
          oldIndex++
          if (newIndex == newSize || oldIndex == oldSize) {
            break@loop
          }
        }
      }
    }
    for (i in newIndex until newSize) {
      updatedPresentations.add(new[i])
    }
    return updatedPresentations
  }

  /**
   * @return true iff updated
   */
  private fun <Content : Any>RootInlayPresentation<Content>.updateIfSame(
    newPresentation: RootInlayPresentation<*>,
    editor: Editor,
    factory: InlayPresentationFactory
  ) : Boolean {
    if (key != newPresentation.key) return false
    @Suppress("UNCHECKED_CAST")
    return update(newPresentation.content as Content, editor, factory)
  }

  /**
   * Note that the range may still be invalid if document doesn't match PSI
   */
  fun getTextRangeWithoutLeadingCommentsAndWhitespaces(element: PsiElement): TextRange {
    val start = SyntaxTraverser.psiApi().children(element).firstOrNull { it !is PsiComment && it !is PsiWhiteSpace } ?: element

    return TextRange.create(start.startOffset, element.endOffset)
  }

  @JvmStatic
  fun isFirstInLine(element: PsiElement): Boolean {
    val prevSibling = element.prevSibling
    return prevSibling is PsiWhiteSpace &&
           (prevSibling.textContains('\n') || prevSibling.getTextRange().startOffset == 0) ||
           element.textRange.startOffset == 0
  }
}