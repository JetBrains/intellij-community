// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService
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
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.EditorHighlightingPredicate
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.*
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.SlowOperations
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

private const val MAX_COUNT_TO_SHOW_HINT = 5

/**
 * Service class responsible for managing and providing functionality for command completion within a project.
 *
 * This class allows filtering and enhancing the behavior of code completion for commands, assisting in the
 * integration of additional matchers, hint rendering, and custom lookup arrangements. It relies on language-specific
 * completion factories to customize behavior according to individual requirements.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class CommandCompletionService(
  val coroutineScope: CoroutineScope,
) : Disposable {

  companion object {
    private val EP_NAME: LanguageExtension<CommandCompletionFactory> = LanguageExtension<CommandCompletionFactory>("com.intellij.codeInsight.completion.command.factory")
  }

  override fun dispose() {
  }

  internal fun filterLookupAfterChar(typed: Char, editor: Editor, file: PsiFile, lookup: LookupImpl): Boolean {
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
    val installed = lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY)
    if (installed == true && nonWrittenFiles) return
    val language = psiFile?.language ?: return
    val completionFactory = getFactory(language)
    val filterSuffix = completionFactory?.filterSuffix() ?: return
    val fullSuffix = completionFactory.suffix() + filterSuffix.toString()
    val editor = InjectedLanguageEditorUtil.getTopLevelEditor(originalEditor)
    if (!nonWrittenFiles) {
      val index = findActualIndex(fullSuffix, editor.document.immutableCharSequence, lookup.lookupOriginalStart)
      val offsetOfFullIndex = lookup.lookupOriginalStart - index
      if (index == 0 || offsetOfFullIndex < 0 ||
          offsetOfFullIndex >= editor.document.textLength ||
          !editor.document.immutableCharSequence.substring(offsetOfFullIndex, editor.caretModel.offset).startsWith(fullSuffix)) {
        if (installed != true) return
        lookup.removeUserData(INSTALLED_ADDITIONAL_MATCHER_KEY)
        lookup.arranger.registerAdditionalMatcher { true }
        lookup.arranger.prefixChanged(lookup)
        lookup.requestResize()
        lookup.refreshUi(false, true)
        lookup.ensureSelectionVisible(true)
        return
      }
    }
    if (installed == true) return
    lookup.putUserData(INSTALLED_ADDITIONAL_MATCHER_KEY, true)
    lookup.showIfMeaningless() // stop hiding
    lookup.arranger.registerAdditionalMatcher(CommandCompletionLookupItemFilter)
    lookup.arranger.prefixChanged(lookup)
    lookup.requestResize()
    lookup.refreshUi(false, true)
    lookup.ensureSelectionVisible(true)
  }

  internal fun addFiltersAndRefreshAfterChar(lookup: LookupImpl, showIfMeaningless: Boolean = true) {
    val userData = lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY)
    if (userData == true) return
    lookup.putUserData(INSTALLED_ADDITIONAL_MATCHER_KEY, true)
    if (showIfMeaningless) {
      lookup.showIfMeaningless() // stop hiding
    }
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
    val applicationCommandCompletionService = ApplicationCommandCompletionService.getInstance()
    val state = applicationCommandCompletionService.state
    if (state.showCounts > MAX_COUNT_TO_SHOW_HINT) return
    state.showCounts += 1
    val inlineElement: Inlay<HintRenderer?> = editor.inlayModel.addInlineElement(endOffset, true, EditorLineStripeTextRenderer("      " + CodeInsightBundle.message("command.completion.filter.hint", completionFactory.filterSuffix())))
                                              ?: return
    Disposer.register(lookup, inlineElement)
    Disposer.register(lookup) { lookup.removeUserData(INSTALLED_HINT) }

    editor.inlayModel.addListener(object : InlayModel.Listener{
      override fun onAdded(inlay: Inlay<*>) {
        if (inlay.offset >= endOffset) {
          lookup.putUserData(INSTALLED_HINT_KEY, false)
          val installedHint = lookup.removeUserData(INSTALLED_HINT)
          if (state.showCounts > MAX_COUNT_TO_SHOW_HINT) {
            state.showCounts = MAX_COUNT_TO_SHOW_HINT
          }
          if (installedHint != null) {
            Disposer.dispose(installedHint)
          }
        }
      }
    }, lookup)

    lookup.putUserData(INSTALLED_HINT, inlineElement)
    lookup.putUserData(INSTALLED_HINT_KEY, true)
  }

  private object CommandCompletionLookupItemFilter : Condition<LookupElement> {
    override fun value(e: LookupElement?): Boolean {
      return e != null && e.`as`(CommandCompletionLookupElement::class.java) != null
    }
  }
}

private val INSTALLED_HINT: Key<Inlay<HintRenderer?>> = Key.create("completion.command.installed.hint")
private val INSTALLED_HINT_KEY: Key<Boolean> = Key.create("completion.command.installed.hint")
private val INSTALLED_ADDITIONAL_MATCHER_KEY: Key<Boolean> = Key.create("completion.command.installed.additional.matcher")
private val INSTALLED_PROMPT_KEY: Key<AtomicBoolean> = Key.create("completion.command.installed.lookup.command.listener")

private val SUPPRESS_PREDICATE_KEY = Key.create<EditorHighlightingPredicate>("completion.command.suppress.completion.predicate")
private val PROMPT_HIGHLIGHTING = Key.create<RangeHighlighter>("completion.command.prompt.highlighting")

private val LOOKUP_HIGHLIGHTING = Key.create<List<RangeHighlighter>>("completion.command.lookup.highlighting")
private val ICON_RENDER = Key.create<Inlay<PresentationRenderer?>>("completion.command.icon.render")
private const val PROMPT_LAYER = HighlighterLayer.ERROR + 10

@ApiStatus.Internal
internal class CommandCompletionListener : LookupManagerListener {

  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return
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
    installLookupIntentionPreviewListener(newLookup)
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

  private fun clearPromptHighlighting(editor: Editor?) {
    val installed = lookup.removeUserData(INSTALLED_PROMPT_KEY) ?: return
    if (!installed.get()) {
      return
    }
    val previousHighlighting = lookup.removeUserData(PROMPT_HIGHLIGHTING)
    previousHighlighting?.let { editor?.markupModel?.removeHighlighter(it) }

    (editor as? EditorImpl)?.removeHighlightingPredicate(SUPPRESS_PREDICATE_KEY)
  }

  private fun clear(editor: Editor?) {
    clearPromptHighlighting(editor)
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
    if (updateItem(item)) return
  }

  private fun updateItem(item: LookupElement?): Boolean {
    val element = item?.`as`(CommandCompletionLookupElement::class.java)
    if (element == null) {
      clear(lookup.editor)
      return true
    }

    if (element.useLookupString) {
      updatePromptHighlighting(lookup, element)
    }
    else {
      clearPromptHighlighting(lookup.editor)
    }
    updateHighlighting(lookup, element)
    return false
  }

  override fun lookupCanceled(event: LookupEvent) {
    val editor = runReadAction { event.lookup.editor }
    clear(editor)
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

  private fun updatePromptHighlighting(lookup: LookupImpl, item: CommandCompletionLookupElement) {
    val installed = ConcurrencyUtil.computeIfAbsent(lookup, INSTALLED_PROMPT_KEY) { AtomicBoolean(false) }
    val startOffset = lookup.lookupOriginalStart - findActualIndex(item.suffix, editor.document.immutableCharSequence,
                                                                   lookup.lookupOriginalStart)
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
    if (updateItem(item)) return
    val element = item?.`as`(CommandCompletionLookupElement::class.java) ?: return
    updateIcon(lookup, element)
  }

  private fun updateHighlighting(lookup: LookupImpl, element: CommandCompletionLookupElement) {
    val project = editor.project ?: return
    val highlightManager = HighlightManager.getInstance(project)
    val previousHighlighting = lookup.removeUserData(LOOKUP_HIGHLIGHTING)
    previousHighlighting?.forEach { t -> highlightManager.removeSegmentHighlighter(editor, t) }
    val startOffset = lookup.lookupOriginalStart -
                      if (nonWrittenFiles) 0
                      else findActualIndex(element.suffix, editor.document.immutableCharSequence,
                                           lookup.lookupOriginalStart)
    val highlightInfo = element.highlighting ?: return
    val rangeHighlighters = mutableListOf<RangeHighlighter>()
    val endOffset = min(highlightInfo.range.endOffset, startOffset)
    if (highlightInfo.range.startOffset <= min(highlightInfo.range.endOffset, startOffset)) {
      highlightManager.addRangeHighlight(editor, highlightInfo.range.startOffset, endOffset, EditorColors.SEARCH_RESULT_ATTRIBUTES, false, rangeHighlighters)
      highlightManager.addRangeHighlight(editor, highlightInfo.range.startOffset, endOffset, highlightInfo.attributesKey, false, rangeHighlighters)
    }
    if (rangeHighlighters.isNotEmpty()) {
      lookup.putUserData(LOOKUP_HIGHLIGHTING, rangeHighlighters)
    }
  }

  override fun dispose() {
    clear(lookup.editor)
  }
}

/**
 * A custom implementation of [CharFilter] that provides specific behavior for handling character inputs
 * during completion, particularly for commands.
 * This implementation works in conjunction with the `CommandCompletionService`.
 * This filter evaluates whether a given character should modify the current completion prefix,
 * select an item, or hide the lookup based on various conditions, such as the presence of
 * specific data in the current lookup or the state of the caret/editor.
 */
@ApiStatus.Internal
internal class CommandCompletionCharFilter : CharFilter() {
  override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup?): Result? {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return null
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
    if (completionService.filterLookupAfterChar(c, editor, psiFile, lookup)) {
      completionService.addFiltersAndRefreshAfterChar(lookup)
      return Result.ADD_TO_PREFIX
    }
    if (offset > 0 && completionFactory.filterSuffix() == c &&
        editor.document.immutableCharSequence[offset - 1] == completionFactory.suffix() &&
        lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY) != true && !lookup.isFocused) return Result.ADD_TO_PREFIX
    val element = lookup.currentItem ?: return null
    if (c == ' ' &&
        findCommandCompletionType(completionFactory, false, offset, editor) is InvocationCommandType.FullLine &&
        !lookup.isFocused &&
        lookup.items.any { it.`as`(CommandCompletionLookupElement::class.java) != null }) {
      return Result.ADD_TO_PREFIX
    }
    element.`as`(CommandCompletionLookupElement::class.java) ?: return null
    return Result.ADD_TO_PREFIX
  }
}

/**
 * A private implementation of the `LookupCustomizer` interface that modifies a lookup instance
 * to insert additional flags.
 */
@ApiStatus.Internal
internal class CommandCompletionLookupCustomizer : LookupCustomizer {
  override fun customizeLookup(lookupImpl: LookupImpl) {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return
    val project = lookupImpl.project
    val service = project.service<CommandCompletionService>()
    val editor = lookupImpl.editor
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    val element: PsiElement? =
      //it is used only in backend in split mode, so it is allowed to be on EDT
      SlowOperations.knownIssue("IJPL-181979").use {
        InjectedLanguageManager.getInstance(project).findInjectedElementAt(psiFile, editor.caretModel.offset)
      }
    val language = element?.language ?: psiFile.language
    val factory = service.getFactory(language)
    if (factory != null) {
      lookupImpl.putUserDataIfAbsent(CUSTOM_DEFAULT_CHAR_FILTERS, true)
    }
  }
}