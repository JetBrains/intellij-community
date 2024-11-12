// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.core

import com.intellij.codeInsight.completion.commands.api.CommandCompletionFactory
import com.intellij.codeInsight.daemon.impl.CommandCompletionServiceApi
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateColors
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorHighlightingPredicate
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

@ApiStatus.Internal
@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class CommandCompletionService(
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

  fun addFilters(lookup: LookupImpl) {
    val userData = lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY)
    if (userData == true) return
    val language = lookup.psiFile?.language ?: return
    val completionFactory = getFactory(language)
    val filterSuffix = completionFactory?.filterSuffix() ?: return
    val index = findActualIndex(completionFactory.suffix() + filterSuffix.toString(), lookup.editor.document.immutableCharSequence, lookup.lookupOriginalStart)
    if (index == 0) return
    if (lookup.lookupOriginalStart - index + 1 < lookup.editor.document.textLength && lookup.editor.document.immutableCharSequence[lookup.lookupOriginalStart - index + 1] != filterSuffix) return
    lookup.putUserData(INSTALLED_ADDITIONAL_MATCHER_KEY, true)
    lookup.arranger.registerAdditionalMatcher(CommandCompletionLookupItemFilter, lookup)
    lookup.requestResize()
    lookup.refreshUi(false, true)
    lookup.ensureSelectionVisible(true)
  }

  fun addFiltersAndRefresh(lookup: LookupImpl) {
    val userData = lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY)
    if (userData == true) return
    lookup.putUserData(INSTALLED_ADDITIONAL_MATCHER_KEY, true)
    lookup.arranger.registerAdditionalMatcher(CommandCompletionLookupItemFilter, lookup)
    lookup.requestResize()
    lookup.refreshUi(false, true)
    lookup.ensureSelectionVisible(true)
  }

  fun getPreviousHighlighting(editor: Editor, document: Document, offset: Int): HighlightingContainer? {
    val highlightInfoContainer = editor.getUserData(PREVIOUS_HIGHLIGHT_INFO_CONTAINER_KEY)
    val actionContainers = editor.getUserData(PREVIOUS_HIGHLIGHT_CACHED_CONTAINER_INFO_CONTAINER_KEY)
    if (highlightInfoContainer == null || actionContainers == null) return null
    if (document.immutableCharSequence.hashCode() != highlightInfoContainer.hashcode) return null
    if (highlightInfoContainer.offset != offset) return null
    val actionContainer = actionContainers.firstOrNull { it.hashcode == highlightInfoContainer.hashcode } ?: return null
    val allActions = mutableListOf<IntentionActionWithTextCaching>()
    allActions.addAll(actionContainer.highlighters.allActions)
    val revertMap: MutableMap<IntentionAction, RangeHighlighterEx> = mutableMapOf()
    for (highlighterEx in highlightInfoContainer.highlighters) {
      val info = HighlightInfo.fromRangeHighlighter(highlighterEx)
      info?.findRegisteredQuickFix { t, u ->
        revertMap[t.action] = highlighterEx
        return@findRegisteredQuickFix null
      }
    }
    val map: MutableMap<IntentionActionWithTextCaching, RangeHighlighterEx?> = mutableMapOf()
    for (action in allActions) {
      val highlighterEx = revertMap[action.action]
      map[action] = highlighterEx
    }
    val newIntentionContainer = CachedIntentions(actionContainer.highlighters.project, actionContainer.highlighters.file, actionContainer.highlighters.editor)

    newIntentionContainer.intentions.addAll(actionContainer.highlighters.intentions)
    newIntentionContainer.errorFixes.addAll(actionContainer.highlighters.errorFixes)
    newIntentionContainer.inspectionFixes.addAll(actionContainer.highlighters.inspectionFixes)
    return HighlightingContainer(newIntentionContainer, map)
  }

  fun cacheActions(editor: Editor, psiFile: PsiFile, intentions: CachedIntentions) {
    if (intentions.allActions.isEmpty()) return
    val data = editor.getUserData(PREVIOUS_HIGHLIGHT_CACHED_CONTAINER_INFO_CONTAINER_KEY)
    val completionFactory = getFactory(psiFile.language) ?: return
    val filterSuffix = completionFactory.filterSuffix() ?: return
    val offset = editor.caretModel.offset
    val index = findActualIndex(completionFactory.suffix() + filterSuffix.toString(), editor.document.immutableCharSequence, offset)
    val newList: MutableList<PreviousActionInfoContainer> = if (index != 0) {
      val previousBeforeDot = data?.firstOrNull()
      val last = data?.firstOrNull()
      if (previousBeforeDot != null && last != null) {
        val newString = editor.document.immutableCharSequence.substring(0, offset - index) + editor.document.immutableCharSequence.substring(offset)
        if (newString.hashCode() == previousBeforeDot.hashcode) {
          mutableListOf(previousBeforeDot)
        }
        else if (editor.document.immutableCharSequence.hashCode() == last.hashcode) {
          data.take((data.size - 1).coerceAtLeast(3)).toMutableList()
        }
        else {
          data.takeLast((data.size - 1).coerceAtLeast(3)).toMutableList()
        }
      }
      else {
        mutableListOf()
      }
    }
    else {
      editor.removeUserData(PREVIOUS_HIGHLIGHT_CACHED_CONTAINER_INFO_CONTAINER_KEY)
      mutableListOf()
    }
    val document = editor.document
    val lineNumber = document.getLineNumber(offset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)
    val container = PreviousActionInfoContainer(intentions, editor.caretModel.offset, editor.document.immutableCharSequence.hashCode(), editor.document.immutableCharSequence.substring(lineStart, lineEnd))
    newList.add(container)
    editor.putUserData(PREVIOUS_HIGHLIGHT_CACHED_CONTAINER_INFO_CONTAINER_KEY, newList.toMutableList())
  }

  @ApiStatus.Internal
  private object CommandCompletionLookupItemFilter : Condition<LookupElement> {
    override fun value(e: LookupElement?): Boolean {
      return e != null && e.`as`(CommandCompletionLookupElement::class.java) != null
    }
  }
}

private val INSTALLED_ADDITIONAL_MATCHER_KEY: Key<Boolean> = Key.create("completion.command.installed.additional.matcher")
private val INSTALLED_LISTENER_KEY: Key<AtomicBoolean> = Key.create("completion.command.installed.lookup.command.listener")
private val SUPPRESS_PREDICATE_KEY = Key.create<EditorHighlightingPredicate>("completion.command.suppress.completion.predicate")
private val PROMPT_HIGHLIGHTING = Key.create<RangeHighlighter>("completion.command.prompt.highlighting")
private val LOOKUP_HIGHLIGHTING = Key.create<List<RangeHighlighter>>("completion.command.lookup.highlighting")
private val ICON_RENDER = Key.create<Inlay<PresentationRenderer?>>("completion.command.icon.render")
private val PREVIOUS_HIGHLIGHT_INFO_CONTAINER_KEY = Key.create<PreviousHighlightInfoContainer>("completion.command.previous.container")
val PREVIOUS_HIGHLIGHT_CACHED_CONTAINER_INFO_CONTAINER_KEY: Key<List<PreviousActionInfoContainer>?> = Key.create<List<PreviousActionInfoContainer>>("completion.cached.command.previous.container")
private const val PROMPT_LAYER = HighlighterLayer.ERROR + 10

private class CommandCompletionListener : LookupManagerListener {

  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (!Registry.`is`("java.completion.command.enabled")) return
    val editor = newLookup?.editor ?: return
    if (editor !is EditorImpl) return
    if (newLookup !is LookupImpl) return
    val completionService = editor.project?.getService(CommandCompletionService::class.java)
    completionService?.addFilters(newLookup)
    val highlightingListener = CommandCompletionHighlightingListener(editor, newLookup, completionService)
    newLookup.addLookupListener(highlightingListener)
    Disposer.register(newLookup, highlightingListener)
  }
}

private class CommandCompletionHighlightingListener(
  val editor: EditorImpl,
  val lookup: LookupImpl,
  val completionService: CommandCompletionService?,
) : LookupListener, Disposable {

  private fun clear(editorImpl: EditorImpl) {
    val installed = lookup.removeUserData(INSTALLED_LISTENER_KEY) ?: return
    if (!installed.get()) {
      return
    }
    val previousHighlighting = lookup.removeUserData(PROMPT_HIGHLIGHTING)
    previousHighlighting?.let { editorImpl.markupModel.removeHighlighter(it) }

    editorImpl.removeHighlightingPredicate(SUPPRESS_PREDICATE_KEY)

    val renderer = lookup.removeUserData(ICON_RENDER)
    renderer?.let { Disposer.dispose(it) }

    val project = editor.project ?: return
    val highlightManager = HighlightManager.getInstance(project)
    val previousLookupHighlighting = lookup.removeUserData(LOOKUP_HIGHLIGHTING)
    previousLookupHighlighting?.forEach { t -> highlightManager.removeSegmentHighlighter(editor, t) }
  }

  override fun uiRefreshed() {
    completionService?.addFilters(lookup)
    val item = lookup.currentItemOrEmpty
    val element = item?.`as`(CommandCompletionLookupElement::class.java)
    if (element == null) {
      clear(editor)
      return
    }
    update(lookup, element)
    updateHighlighting(lookup, element)
    super.uiRefreshed()
  }

  override fun lookupCanceled(event: LookupEvent) {
    clear(editor)
    super.lookupCanceled(event)
  }

  private fun updateIcon(lookup: LookupImpl, element: CommandCompletionLookupElement) {
    val renderer = lookup.getUserData(ICON_RENDER)
    renderer?.let { Disposer.dispose(it) }
    if (element.icon != null) {
      val factory = PresentationFactory(editor)
      val iconPresentation = factory.icon(element.icon)
      val presentationRenderer = PresentationRenderer(iconPresentation)
      val inlay: Inlay<PresentationRenderer?>? = lookup.editor.inlayModel.addInlineElement(element.startOffset, true, presentationRenderer)
      if (inlay != null) {
        lookup.putUserData(ICON_RENDER, inlay)
      }
    }
  }

  private fun update(lookup: LookupImpl, item: CommandCompletionLookupElement) {
    var installed = lookup.putUserDataIfAbsent(INSTALLED_LISTENER_KEY, AtomicBoolean(false))
    val startOffset = lookup.lookupOriginalStart - findActualIndex(item.suffix, editor.document.immutableCharSequence, lookup.lookupOriginalStart)
    val endOffset = lookup.editor.caretModel.offset
    if (!installed.get()) {
      editor.addHighlightingPredicate(SUPPRESS_PREDICATE_KEY, EditorHighlightingPredicate { highlighter ->
        val attributesKey = highlighter.textAttributesKey ?: return@EditorHighlightingPredicate true

        if (!(attributesKey == CodeInsightColors.ERRORS_ATTRIBUTES || attributesKey == CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES || attributesKey == CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING || attributesKey == CodeInsightColors.RUNTIME_ERROR)) {
          return@EditorHighlightingPredicate true
        }
        return@EditorHighlightingPredicate !TextRange(startOffset, endOffset).intersects(TextRange(highlighter.startOffset, highlighter.endOffset))
      })
      installed.set(true)
    }
    val previousHighlighting = lookup.getUserData(PROMPT_HIGHLIGHTING)
    previousHighlighting?.let { lookup.editor.markupModel.removeHighlighter(it) }
    val highlighter = lookup.editor.markupModel.addRangeHighlighter(TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES, startOffset, endOffset, PROMPT_LAYER, HighlighterTargetArea.EXACT_RANGE)
    lookup.putUserData(PROMPT_HIGHLIGHTING, highlighter)
  }

  override fun currentItemChanged(event: LookupEvent) {
    val lookup = event.lookup
    if (lookup !is LookupImpl) return
    val item = event.item
    val element = item?.`as`(CommandCompletionLookupElement::class.java)
    if (element == null) {
      clear(editor)
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
    val startOffset = lookup.lookupOriginalStart - findActualIndex(element.suffix, editor.document.immutableCharSequence, lookup.lookupOriginalStart)
    val highlightInfo = element.highlighting ?: return
    val rangeHighlighters = mutableListOf<RangeHighlighter>()
    val endOffset = min(highlightInfo.range().endOffset, startOffset)
    if (highlightInfo.range().startOffset <= endOffset) {
      highlightManager.addRangeHighlight(lookup.editor, highlightInfo.range().startOffset, endOffset, EditorColors.SEARCH_RESULT_ATTRIBUTES, highlightInfo.hideByTextChange, rangeHighlighters)
    }
    if (lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY) == true) {
      for (it in lookup.items) {
        val element = it?.`as`(CommandCompletionLookupElement::class.java) ?: continue
        val info = element.highlighting ?: continue
        val endOffset = min(info.range().endOffset, startOffset)
        if (info.range().startOffset <= min(info.range().endOffset, startOffset)) {
          highlightManager.addRangeHighlight(lookup.editor, info.range().startOffset, endOffset, info.attributesKey, highlightInfo.hideByTextChange, rangeHighlighters)
        }
      }
    }
    if (rangeHighlighters.isNotEmpty()) {
      lookup.putUserData(LOOKUP_HIGHLIGHTING, rangeHighlighters)
    }
  }

  override fun dispose() {
    clear(editor)
  }
}

private class CommandCompletionCharFilter : CharFilter() {
  override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup?): Result? {
    if (!Registry.`is`("java.completion.command.enabled")) return null
    if (lookup !is LookupImpl) return null
    val completionService = lookup.project.service<CommandCompletionService>()
    val psiFile = lookup.psiFile ?: return null
    val completionFactory = completionService.getFactory(psiFile.language) ?: return null
    val offset = lookup.editor.caretModel.offset
    if (offset > 0 && completionFactory.filterSuffix() == c &&
        lookup.editor.document.immutableCharSequence[offset - 1] == completionFactory.suffix() &&
        lookup.getUserData(INSTALLED_ADDITIONAL_MATCHER_KEY) != true && !lookup.isFocused) return Result.ADD_TO_PREFIX
    val element = lookup.currentItem ?: return null
    element.`as`(CommandCompletionLookupElement::class.java) ?: return null
    if (completionService.filterLookup(c, lookup.editor, psiFile, lookup)) {
      completionService.addFiltersAndRefresh(lookup)
    }
    return Result.ADD_TO_PREFIX
  }
}

private class CommandTypeHandler : TypedHandlerDelegate() {
  override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
    if (!Registry.`is`("java.completion.command.enabled")) return super.beforeCharTyped(c, project, editor, file, fileType)
    val commandCompletionService = project.getService(CommandCompletionService::class.java)
    val completionFactory = commandCompletionService.getFactory(file.language)
    if (completionFactory?.suffix() != c) return super.beforeCharTyped(c, project, editor, file, fileType)
    val offset = editor.caretModel.offset
    if (completionFactory.suffix() == completionFactory.filterSuffix()) {
      val immutableCharSequence = editor.document.immutableCharSequence
      if (immutableCharSequence.isNotEmpty() && immutableCharSequence[offset - 1] == completionFactory.suffix()) {
        return super.beforeCharTyped(c, project, editor, file, fileType)
      }
    }
    collectHighlighting(editor)
    return super.beforeCharTyped(c, project, editor, file, fileType)
  }

  private fun collectHighlighting(editor: Editor) {
    editor.removeUserData(PREVIOUS_HIGHLIGHT_INFO_CONTAINER_KEY)
    val document = editor.document
    val project = editor.project ?: return
    val offset = editor.caretModel.offset
    val immutableCharSequence = document.immutableCharSequence

    val lineNumber = document.getLineNumber(offset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)

    val highlighters = mutableListOf<RangeHighlighterEx>()

    DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.INFORMATION, lineStart, (offset + 1).coerceAtLeast(immutableCharSequence.length)) { info ->
      if (info is HighlightInfo && info.highlighter is RangeHighlighterEx) {
        highlighters.add(info.highlighter)
      }
      return@processHighlights true
    }

    val container = PreviousHighlightInfoContainer(highlighters, offset, immutableCharSequence.hashCode(), immutableCharSequence.substring(lineStart, lineEnd))
    editor.putUserData(PREVIOUS_HIGHLIGHT_INFO_CONTAINER_KEY, container)
  }
}


private data class PreviousHighlightInfoContainer(
  val highlighters: List<RangeHighlighterEx>,
  val offset: Int,
  val hashcode: Int,
  val lineText: String,
)

data class PreviousActionInfoContainer(
  val highlighters: CachedIntentions,
  val offset: Int,
  val hashcode: Int,
  val lineText: String,
)

data class HighlightingContainer(val cachedIntentions: CachedIntentions, val map: Map<IntentionActionWithTextCaching, RangeHighlighterEx?>)

class CommandCompletionServiceImpl(val project: Project) : CommandCompletionServiceApi() {
  override fun cacheActions(editor: Editor, file: PsiFile, intentions: CachedIntentions) {
    if (!Registry.`is`("java.completion.command.enabled")) return
    val completionService = project.getService<CommandCompletionService>(CommandCompletionService::class.java)
    completionService?.cacheActions(editor, file, intentions)
  }
}