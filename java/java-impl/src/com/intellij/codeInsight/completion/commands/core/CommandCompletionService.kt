// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.core

import com.intellij.codeInsight.completion.commands.api.CommandCompletionFactory
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.editorLineStripeHint.EditorLineStripeTextRenderer
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.CharFilter.CUSTOM_DEFAULT_CHAR_FILTERS
import com.intellij.codeInsight.lookup.impl.LookupCustomizer
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateColors
import com.intellij.java.JavaBundle
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.EditorHighlightingPredicate
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

@ApiStatus.Internal
@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
internal class CommandCompletionService(
  private val project: Project,
  val coroutineScope: CoroutineScope,
) : Disposable {

  companion object {
    private val EP_NAME: LanguageExtension<CommandCompletionFactory> = LanguageExtension<CommandCompletionFactory>("com.intellij.codeInsight.completion.command.factory")
  }

  override fun dispose() {
  }

  fun filterLookup(typed: Char, editor: Editor, file: PsiFile, lookup: LookupImpl): Boolean {
    if (lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY) == true) return false
    val factory = getFactory(file.language)
    if (factory?.filterSuffix() != typed) return false
    val offset = editor.caretModel.offset
    if (offset == 0) return false
    return factory.suffix() == editor.document.immutableCharSequence[offset - 1]
  }

  fun getFactory(language: Language): CommandCompletionFactory? {
    return EP_NAME.forLanguage(language)
  }

  fun addFilters(lookup: LookupImpl, nonWrittenFiles: Boolean, psiFile: PsiFile?, originalEditor: Editor) {
    val userData = lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY)
    if (userData == true) return
    val language = psiFile?.language ?: return
    val completionFactory = getFactory(language)
    val filterSuffix = completionFactory?.filterSuffix() ?: return
    val fullSuffix = completionFactory.suffix() + filterSuffix.toString()
    val editor = InjectedLanguageEditorUtil.getTopLevelEditor(originalEditor)
    if (!nonWrittenFiles) {
      val index = findActualIndex(fullSuffix, editor.document.immutableCharSequence, lookup.lookupOriginalStart)
      if (index == 0) return
      val offsetOfFullIndex = lookup.lookupOriginalStart - index
      if (offsetOfFullIndex < 0 ||
          offsetOfFullIndex >= editor.document.textLength ||
          editor.document.immutableCharSequence.substring(offsetOfFullIndex, lookup.lookupOriginalStart) != fullSuffix) return
    }
    lookup.putUserData(INSTALLED_ADDITIONAL_MATCHER_KEY, true)
    lookup.showIfMeaningless() // stop hiding
    lookup.arranger.registerAdditionalMatcher(CommandCompletionLookupItemFilter)
    lookup.arranger.prefixChanged(lookup)
    lookup.requestResize()
    lookup.refreshUi(false, true)
    lookup.ensureSelectionVisible(true)
  }

  fun addFiltersAndRefresh(lookup: LookupImpl) {
    val userData = lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY)
    if (userData == true) return
    lookup.putUserData(INSTALLED_ADDITIONAL_MATCHER_KEY, true)
    lookup.showIfMeaningless() // stop hiding
    lookup.arranger.registerAdditionalMatcher(CommandCompletionLookupItemFilter)
    lookup.arranger.prefixChanged(lookup)
    lookup.requestResize()
    lookup.refreshUi(false, true)
    lookup.ensureSelectionVisible(true)
  }

  fun setHint(lookup: LookupImpl, editor: EditorImpl, nonWrittenFiles: Boolean) {
    if (lookup.getUserData(INSTALLED_HINT_KEY) == false) return
    lookup.putUserData(INSTALLED_HINT_KEY, false)
    val psiFile = lookup.psiFile ?: return
    val completionService = lookup.project.service<CommandCompletionService>()
    val completionFactory = completionService.getFactory(psiFile.language) ?: return
    val fullSuffix = completionFactory.suffix() + completionFactory.filterSuffix().toString()
    val editor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    val index = if (nonWrittenFiles) 0 else findActualIndex(fullSuffix, editor.document.immutableCharSequence, lookup.lookupOriginalStart)
    val startOffset = lookup.lookupOriginalStart - index
    val endOffset = editor.caretModel.offset
    if (endOffset - startOffset != 1) return
    if (lookup.items.none { it.`as`(CommandCompletionLookupElement::class.java) != null }) return
    if (editor.inlayModel.getInlineElementsInRange(startOffset, endOffset).isNotEmpty()) return
    val applicationCommandCompletionService = ApplicationManager.getApplication().getService(ApplicationCommandCompletionService::class.java)
    val state = applicationCommandCompletionService.state
    if (state.showCounts > 5) return
    state.showCounts += 1
    val inlineElement: Inlay<HintRenderer?> = editor.inlayModel.addInlineElement(endOffset, true, EditorLineStripeTextRenderer("      " + JavaBundle.message("command.completion.filter.hint")))
                                              ?: return
    Disposer.register(lookup, inlineElement)
    Disposer.register(lookup) { lookup.removeUserData(INSTALLED_HINT) }

    lookup.putUserData(INSTALLED_HINT, inlineElement)
    lookup.putUserData(INSTALLED_HINT_KEY, true)
  }

  @ApiStatus.Internal
  private object CommandCompletionLookupItemFilter : Condition<LookupElement> {
    override fun value(e: LookupElement?): Boolean {
      return e != null && e.`as`(CommandCompletionLookupElement::class.java) != null
    }
  }
}

private val INSTALLED_HINT: Key<Inlay<HintRenderer?>> = Key.create("completion.command.installed.hint")
private val INSTALLED_HINT_KEY: Key<Boolean> = Key.create("completion.command.installed.hint")
private val INSTALLED_ADDITIONAL_MATCHER_KEY: Key<Boolean> = Key.create("completion.command.installed.additional.matcher")
private val INSTALLED_LISTENER_KEY: Key<AtomicBoolean> = Key.create("completion.command.installed.lookup.command.listener")
private val SUPPRESS_PREDICATE_KEY = Key.create<EditorHighlightingPredicate>("completion.command.suppress.completion.predicate")
private val PROMPT_HIGHLIGHTING = Key.create<RangeHighlighter>("completion.command.prompt.highlighting")
private val LOOKUP_HIGHLIGHTING = Key.create<List<RangeHighlighter>>("completion.command.lookup.highlighting")
private val ICON_RENDER = Key.create<Inlay<PresentationRenderer?>>("completion.command.icon.render")
private const val PROMPT_LAYER = HighlighterLayer.ERROR + 10

private class CommandCompletionListener : LookupManagerListener {

  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (!Registry.`is`("java.completion.command.enabled")) return
    var editor = newLookup?.editor ?: return
    val originalEditor = editor.getUserData(ORIGINAL_EDITOR)
    var psiFile = newLookup.psiFile ?: return
    val project = newLookup.project
    var nonWrittenFiles = false
    if (originalEditor != null) {
      editor = originalEditor.first
      psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) ?: return
      nonWrittenFiles = true
    }
    val topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(psiFile)
    if (topLevelFile?.virtualFile == null || topLevelFile.virtualFile is LightVirtualFile) {
      return
    }
    val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    if (topLevelEditor !is EditorImpl) return
    if (newLookup !is LookupImpl) return
    val completionService = editor.project?.getService(CommandCompletionService::class.java)
    completionService?.addFilters(newLookup, nonWrittenFiles, psiFile, editor)
    val highlightingListener = CommandCompletionHighlightingListener(topLevelEditor, newLookup, psiFile, nonWrittenFiles, completionService)
    newLookup.addLookupListener(highlightingListener)
    Disposer.register(newLookup, highlightingListener)
  }
}

private class CommandCompletionHighlightingListener(
  val editor: EditorImpl,
  val lookup: LookupImpl,
  val psiFile: PsiFile,
  val nonWrittenFiles: Boolean,
  val completionService: CommandCompletionService?,
) : LookupListener, Disposable {

  private fun clear(editor: Editor?) {
    val installed = lookup.removeUserData(INSTALLED_LISTENER_KEY) ?: return
    if (!installed.get()) {
      return
    }
    val previousHighlighting = lookup.removeUserData(PROMPT_HIGHLIGHTING)
    previousHighlighting?.let { editor?.markupModel?.removeHighlighter(it) }

    (editor as? EditorImpl)?.removeHighlightingPredicate(SUPPRESS_PREDICATE_KEY)

    val renderer = lookup.removeUserData(ICON_RENDER)
    renderer?.let { Disposer.dispose(it) }

    val project = editor?.project ?: return
    val highlightManager = HighlightManager.getInstance(project)
    val previousLookupHighlighting = lookup.removeUserData(LOOKUP_HIGHLIGHTING)
    previousLookupHighlighting?.forEach { t -> highlightManager.removeSegmentHighlighter(editor, t) }
  }

  override fun uiRefreshed() {
    completionService?.addFilters(lookup, nonWrittenFiles, psiFile, editor)
    val item = lookup.currentItemOrEmpty
    val element = item?.`as`(CommandCompletionLookupElement::class.java)
    if (element == null) {
      clear(lookup.editor)
      return
    }
    update(lookup, element)
    updateHighlighting(lookup, element)
    super.uiRefreshed()
  }

  override fun lookupCanceled(event: LookupEvent) {
    clear(event.lookup.editor)
    super.lookupCanceled(event)
  }

  private fun updateIcon(lookup: LookupImpl, element: CommandCompletionLookupElement) {
    if (lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY) != true) {
      return
    }
    val renderer = lookup.getUserData(ICON_RENDER)
    renderer?.let { Disposer.dispose(it) }
    if (element.icon != null) {
      val factory = PresentationFactory(editor)
      val iconPresentation = factory.icon(element.icon)
      val presentationRenderer = PresentationRenderer(iconPresentation)
      val lookupEditor = InjectedLanguageEditorUtil.getTopLevelEditor(lookup.editor)
      val inlay: Inlay<PresentationRenderer?>? =
        if (nonWrittenFiles) {
          lookupEditor.inlayModel.addInlineElement(0, false, presentationRenderer)
        }
        else {
          lookupEditor.inlayModel.addInlineElement(element.hostStartOffset, true, presentationRenderer)
        }
      if (inlay != null) {
        lookup.putUserData(ICON_RENDER, inlay)
      }
    }
  }

  private fun update(lookup: LookupImpl, item: CommandCompletionLookupElement) {
    val installed = ConcurrencyUtil.computeIfAbsent(lookup, INSTALLED_LISTENER_KEY) { AtomicBoolean(false) }
    val startOffset = lookup.lookupOriginalStart - findActualIndex(item.suffix, editor.document.immutableCharSequence, lookup.lookupOriginalStart)
    val lookupEditor = InjectedLanguageEditorUtil.getTopLevelEditor(lookup.editor)
    val endOffset = lookupEditor.caretModel.offset
    if (!installed.get()) {
      editor.addHighlightingPredicate(SUPPRESS_PREDICATE_KEY, EditorHighlightingPredicate { highlighter ->
        val attributesKey = highlighter.textAttributesKey ?: return@EditorHighlightingPredicate true

        if (!(attributesKey == CodeInsightColors.ERRORS_ATTRIBUTES || attributesKey == CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES || attributesKey == CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING || attributesKey == CodeInsightColors.RUNTIME_ERROR)) {
          return@EditorHighlightingPredicate true
        }
        return@EditorHighlightingPredicate !TextRange(startOffset, endOffset).intersects(highlighter.textRange)
      })
      installed.set(true)
    }
    val previousHighlighting = lookup.getUserData(PROMPT_HIGHLIGHTING)
    previousHighlighting?.let { lookupEditor.markupModel.removeHighlighter(it) }
    val highlighter = lookupEditor.markupModel.addRangeHighlighter(TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES, startOffset, endOffset, PROMPT_LAYER, HighlighterTargetArea.EXACT_RANGE)
    lookup.putUserData(PROMPT_HIGHLIGHTING, highlighter)
  }

  override fun currentItemChanged(event: LookupEvent) {
    val lookup = event.lookup
    if (lookup !is LookupImpl) return
    completionService?.setHint(lookup, editor, nonWrittenFiles)
    val item = event.item
    val element = item?.`as`(CommandCompletionLookupElement::class.java)
    if (element == null) {
      clear(lookup.editor)
      return
    }
    update(lookup, element)
    updateIcon(lookup, element)
    updateHighlighting(lookup, element)
    super.currentItemChanged(event)
  }

  private fun updateHighlighting(lookup: LookupImpl, element: CommandCompletionLookupElement) {
    val project = editor.project ?: return
    val highlightManager = HighlightManager.getInstance(project)
    val previousHighlighting = lookup.removeUserData(LOOKUP_HIGHLIGHTING)
    previousHighlighting?.forEach { t -> highlightManager.removeSegmentHighlighter(editor, t) }
    val startOffset = lookup.lookupOriginalStart -
                      if (nonWrittenFiles) 0 else findActualIndex(element.suffix, editor.document.immutableCharSequence, lookup.lookupOriginalStart)
    val highlightInfo = element.highlighting ?: return
    val rangeHighlighters = mutableListOf<RangeHighlighter>()
    val endOffset = min(highlightInfo.range.endOffset, startOffset)
    if (highlightInfo.range.startOffset <= endOffset) {
      highlightManager.addRangeHighlight(editor, highlightInfo.range.startOffset, endOffset, EditorColors.SEARCH_RESULT_ATTRIBUTES, false, rangeHighlighters)
    }
    if (lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY) == true) {
      for (info in lookup.items
        .mapNotNull { it?.`as`(CommandCompletionLookupElement::class.java) }
        .mapNotNull { it.highlighting }
        .sortedByDescending { it.priority }) {
        val endOffset = min(info.range.endOffset, startOffset)
        if (info.range.startOffset <= min(info.range.endOffset, startOffset)) {
          highlightManager.addRangeHighlight(editor, info.range.startOffset, endOffset, info.attributesKey, false, rangeHighlighters)
        }
      }
    }
    if (rangeHighlighters.isNotEmpty()) {
      lookup.putUserData(LOOKUP_HIGHLIGHTING, rangeHighlighters)
    }
  }

  override fun dispose() {
    clear(lookup.editor)
  }
}

private class CommandCompletionCharFilter : CharFilter() {
  override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup?): Result? {
    if (!Registry.`is`("java.completion.command.enabled")) return null
    if (lookup !is LookupImpl) return null
    val completionService = lookup.project.service<CommandCompletionService>()
    val installedHint = lookup.removeUserData(INSTALLED_HINT)
    if (installedHint != null) {
      Disposer.dispose(installedHint)
    }
    val originalEditor = lookup.editor.getUserData(ORIGINAL_EDITOR)
    if (originalEditor != null) return Result.ADD_TO_PREFIX
    val psiFile = lookup.psiFile ?: return null
    val completionFactory = completionService.getFactory(psiFile.language) ?: return null
    val editor = InjectedLanguageEditorUtil.getTopLevelEditor(lookup.editor)
    val offset = editor.caretModel.offset
    if (completionService.filterLookup(c, editor, psiFile, lookup)) {
      completionService.addFiltersAndRefresh(lookup)
      return Result.ADD_TO_PREFIX
    }
    if (offset > 0 && completionFactory.filterSuffix() == c &&
        editor.document.immutableCharSequence[offset - 1] == completionFactory.suffix() &&
        lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY) != true && !lookup.isFocused) return Result.ADD_TO_PREFIX
    val element = lookup.currentItem ?: return null
    element.`as`(CommandCompletionLookupElement::class.java) ?: return null
    return Result.ADD_TO_PREFIX
  }
}

private class CommandCompletionLookupCustomizer : LookupCustomizer {
  override fun customizeLookup(lookupImpl: LookupImpl) {
    if (!Registry.`is`("java.completion.command.enabled")) return
    val project = lookupImpl.project
    val service = project.service<CommandCompletionService>()
    val editor = lookupImpl.editor
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    val element: PsiElement? = InjectedLanguageManager.getInstance(project).findInjectedElementAt(psiFile, editor.caretModel.offset)
    val language = element?.language ?: psiFile.language
    val factory = service.getFactory(language)
    if (factory != null) {
      lookupImpl.putUserDataIfAbsent(CUSTOM_DEFAULT_CHAR_FILTERS, true)
    }
  }
}