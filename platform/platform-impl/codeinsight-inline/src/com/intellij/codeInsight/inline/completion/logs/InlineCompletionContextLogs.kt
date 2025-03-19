// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.features.InlineCompletionFeaturesCollector
import com.intellij.codeInsight.inline.completion.features.InlineCompletionFeaturesScopeAnalyzer.ScopeType
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import com.intellij.util.concurrency.annotations.RequiresReadLock

internal object InlineCompletionContextLogs {
  @RequiresReadLock
  fun getFor(request: InlineCompletionRequest): List<EventPair<*>> {
    val element = if (request.startOffset == 0) null else request.file.findElementAt(request.startOffset - 1)
    val simple = captureSimple(request.file, request.editor, request.startOffset, element)
    val typingFeatures = getTypingSpeedFeatures()
    val featureCollectorBased = InlineCompletionFeaturesCollector.get(request.file.language)?.let {
      captureFeatureCollectorBased(request.file, request.startOffset, it, element)
    }
    return simple + typingFeatures + featureCollectorBased.orEmpty()
  }

  private fun captureSimple(psiFile: PsiFile, editor: Editor, offset: Int, element: PsiElement?): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>()

    element?.let { result.add(Logs.ELEMENT_PREFIX_LENGTH with (offset - it.textOffset)) }

    val logicalPosition = editor.offsetToLogicalPosition(offset)
    val lineNumber = logicalPosition.line

    result.add(Logs.LINE_NUMBER.with(lineNumber))
    result.add(Logs.COLUMN_NUMBER.with(logicalPosition.column))
    result.add(Logs.FILE_LINE_COUNT.with(editor.document.lineCount))

    val lineStartOffset = editor.document.getLineStartOffset(lineNumber)
    val lineEndOffset = editor.document.getLineEndOffset(lineNumber)

    val linePrefix = editor.document.getText(TextRange(lineStartOffset, offset))
    val lineSuffix = editor.document.getText(TextRange(offset, lineEndOffset))

    if (linePrefix.isNotBlank()) {
      result.add(Logs.IS_WHITE_SPACE_BEFORE_CARET.with(linePrefix.last().isWhitespace()))
      val trimmedPrefix = linePrefix.trim()
      result.add(Logs.SYMBOLS_IN_LINE_BEFORE_CARET.with(trimmedPrefix.length))
      CharCategory.find(trimmedPrefix.last())?.let {
        result.add(Logs.NON_SPACE_SYMBOL_BEFORE_CARET.with(it))
      }
    }
    if (lineSuffix.isNotBlank()) {
      result.add(Logs.IS_WHITE_SPACE_AFTER_CARET.with(lineSuffix.first().isWhitespace()))
      val trimmedSuffix = lineSuffix.trim()
      result.add(Logs.SYMBOLS_IN_LINE_AFTER_CARET.with(trimmedSuffix.length))
      CharCategory.find(trimmedSuffix.first())?.let {
        result.add(Logs.NON_SPACE_SYMBOL_AFTER_CARET.with(it))
      }
    }
    val document = editor.document
    val (previousNonEmptyLineNumber, previousNonEmptyLineText) = document.findNonBlankLine(lineNumber, false)
    result.add(Logs.PREVIOUS_EMPTY_LINES_COUNT.with(lineNumber - previousNonEmptyLineNumber - 1))
    if (previousNonEmptyLineText != null) {
      result.add(Logs.PREVIOUS_NON_EMPTY_LINE_LENGTH.with(previousNonEmptyLineText.length))
    }
    val (followingNonEmptyLineNumber, followingNonEmptyLineText) = document.findNonBlankLine(lineNumber, true)
    result.add(Logs.FOLLOWING_EMPTY_LINES_COUNT.with(followingNonEmptyLineNumber - lineNumber - 1))
    if (followingNonEmptyLineText != null) {
      result.add(Logs.FOLLOWING_NON_EMPTY_LINE_LENGTH.with(followingNonEmptyLineText.length))
    }

    psiFile.findElementAt(offset - 1)?.let { result.addPsiParents(it) }
    return result
  }

  private fun Document.findNonBlankLine(lineNumber: Int, following: Boolean): Pair<Int, String?> {
    val delta = if (following) 1 else -1
    var n = lineNumber
    var text: String? = null
    while (n in 0..<lineCount && text == null) {
      n += delta
      text = getNonBlankLineOrNull(n)
    }
    return n to text
  }

  private fun Document.getNonBlankLineOrNull(line: Int): String? {
    if (line !in 0..<lineCount) return null
    val res = getText(TextRange(getLineStartOffset(line), getLineEndOffset(line)))
    return res.trim().ifEmpty { null }
  }

  private fun MutableList<EventPair<*>>.addPsiParents(element: PsiElement) {
    val parents = element.parents(false).toList()
    Logs.PARENT_FEATURES.forEachIndexed { i, parentFeature ->
      parents.getOrNull(i)
        ?.let { add(parentFeature with it.javaClass) }
    }
  }

  private fun captureFeatureCollectorBased(file: PsiFile, offset: Int, featuresCollector: InlineCompletionFeaturesCollector, element: PsiElement?): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>()
    result.addAll(addImportFeatures(featuresCollector, file))
    result.addAll(getExtendedScopeFeatures(featuresCollector, file, offset))

    element ?: return result

    featuresCollector.getNumOfPrevQualifiers(element)?.let {
      result.add(Logs.NUMB_OF_PREV_QUALIFIERS with it)
    }
    result.addAll(featuresCollector.addKeyWordFeatures(element))
    result.addAll(featuresCollector.addArgumentsFeatures(element))
    result.addAll(featuresCollector.addBracketFeatures(element))

    featuresCollector.isInConditionalStatement(element)?.let { result.add(Logs.IS_IN_CONDITIONAL_STATEMENT with it) }
    featuresCollector.isInForStatement(element)?.let { result.add(Logs.IS_IN_FOR_STATEMENT with it) }
    result.add(Logs.BLOCK_STATEMENT_LEVEL with featuresCollector.getBlockStatementLevel(element))

    return result
  }

  private fun getExtendedScopeFeatures(featuresCollector: InlineCompletionFeaturesCollector, file: PsiFile, offset: Int): List<EventPair<*>> {
    val scopeFeatures = featuresCollector.getExtendedScopeFeatures(file, offset)
    return listOf(scopeFeatures.scopeFeatures, scopeFeatures.parentScopeFeatures,
                  scopeFeatures.grandParentScopeFeatures, scopeFeatures.prevSiblingScopeFeatures,
                  scopeFeatures.nextSiblingScopeFeatures).flatMapIndexed { idx, it -> getScopeFeatures(idx, it) }.toList()
  }

  private fun getScopeFeatures(idx: Int, scopeFeatures: InlineCompletionFeaturesCollector.ScopeFeatures?): List<EventPair<*>> {
    if (scopeFeatures == null) return emptyList()
    return buildList {
      add(Logs.SCOPE_TYPE[idx] with scopeFeatures.type)
      add(Logs.SCOPE_INIT[idx] with scopeFeatures.isInit)
      add(Logs.SCOPE_NUM_LINES[idx] with scopeFeatures.numLines)
      add(Logs.SCOPE_NUM_SYMBOLS[idx] with scopeFeatures.numSymbols)
      add(Logs.SCOPE_LINE_OFFSET[idx] with scopeFeatures.lineOffset)
      add(Logs.SCOPE_OFFSET[idx] with scopeFeatures.offset)
      scopeFeatures.valuableSymbolsAfter?.let { add(Logs.SCOPE_VALUABLE_SYMBOLS_AFTER[idx] with it) }
      scopeFeatures.valuableSymbolsBefore?.let { add(Logs.SCOPE_VALUABLE_SYMBOLS_BEFORE[idx] with it) }
      scopeFeatures.hasErrorPsi?.let { add(Logs.SCOPE_HAS_ERROR_PSI[idx] with it) }
    }
  }

  private fun addImportFeatures(featuresCollector: InlineCompletionFeaturesCollector, file: PsiFile): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>()

    val allImports = featuresCollector.getAllImports(file)
    val allImportsCount = allImports.size

    result.add(Logs.IMPORTS_COUNT with allImportsCount)

    if (allImportsCount != 0) {
      val sourceNames = featuresCollector.getSourceNames(allImports)

      featuresCollector.classifyByImportsToTopics(sourceNames).forEach { (topic, value) ->
        val field = Logs.TOPIC_TO_FIELD[topic]
        if (field != null) {
          result.add(field with value)
        }
      }

      val popularLibraryImportsCount = featuresCollector.countPopularLibraries(sourceNames)
      result.add(Logs.POPULAR_LIBRARY_IMPORTS_COUNT with popularLibraryImportsCount)
      result.add(Logs.POPULAR_LIBRARY_IMPORTS_RATIO with popularLibraryImportsCount.toFloat() / allImportsCount)

      val libraryImportsCount = featuresCollector.countLibraries(file.project, allImports)
      if (libraryImportsCount != null) {
        result.add(Logs.LIBRARY_IMPORTS_COUNT with libraryImportsCount)
        result.add(Logs.LIBRARY_IMPORTS_RATIO with libraryImportsCount.toFloat() / allImportsCount)
      }
    }
    else {
      result.add(Logs.POPULAR_LIBRARY_IMPORTS_COUNT with 0)
      result.add(Logs.LIBRARY_IMPORTS_COUNT with 0)
    }

    return result
  }

  private fun InlineCompletionFeaturesCollector.addKeyWordFeatures(element: PsiElement): List<EventPair<*>> {
    fun <T> zipLogs(logs: List<EventField<T>>, values: List<T>): List<EventPair<T>> {
      return logs.zip(values).map { (log, value) -> log with value }
    }

    return zipLogs(listOf(Logs.PREV_NEIGHBOUR_KEYWORD_1, Logs.PREV_NEIGHBOUR_KEYWORD_2), getPrevNeighboursKeywordIds(element)) +
           zipLogs(listOf(Logs.PREV_SAME_LINE_KEYWORD_1, Logs.PREV_SAME_LINE_KEYWORD_2), getPrevKeywordsIdsInTheSameLine(element)) +
           zipLogs(listOf(Logs.PREV_SAME_COLUMN_KEYWORD_1, Logs.PREV_SAME_COLUMN_KEYWORD_2), getPrevKeywordsIdsInTheSameColumn(element))
  }

  private fun InlineCompletionFeaturesCollector.addArgumentsFeatures(element: PsiElement): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>()

    getArgumentFeatures(element)?.let {
      with(it) {
        result.add(Logs.IS_IN_ARGUMENTS with isInArguments)
        result.add(Logs.IS_DIRECTLY_IN_ARGUMENTS_CONTEXT with isDirectlyInArgumentContext)
        argumentIndex?.let { result.add(Logs.ARGUMENT_INDEX with it) }
        argumentsSize?.let { result.add(Logs.ARGUMENTS_SIZE with it) }
        haveNamedArgLeft?.let { result.add(Logs.HAVE_NAMED_ARG_LEFT with it) }
        haveNamedArgRight?.let { result.add(Logs.HAVE_NAMED_ARG_RIGHT with it) }
      }
    }
    return result
  }

  private fun InlineCompletionFeaturesCollector.addBracketFeatures(element: PsiElement): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>()

    getBracketFeatures(element)?.let {
      with(it) {
        result.add(Logs.HAVE_OPENING_PARENTHESIS_LEFT with haveOpeningParenthesisOnTheLeft)
        result.add(Logs.HAVE_OPENING_BRACKET_LEFT with haveOpeningBracketOnTheLeft)
        result.add(Logs.HAVE_OPENING_BRACE_LEFT with haveOpeningBraceOnTheLeft)
        result.add(Logs.HAVE_OPENING_ANGLE_BRACKET_LEFT with haveOpeningAngleBracketOnTheLeft)
      }
    }
    return result
  }

  private fun getTypingSpeedFeatures(): List<EventPair<*>> = buildList {
    val tracker = TypingSpeedTracker.getInstance()
    tracker.getTimeSinceLastTyping()?.let {
      add(Logs.TIME_SINCE_LAST_TYPING with it)
      addAll(tracker.getTypingSpeedEventPairs().map { it.first })
    }
  }

  private object Logs : PhasedLogs(InlineCompletionLogsContainer.Phase.INLINE_API_STARTING) {
    val ELEMENT_PREFIX_LENGTH = register(EventFields.Int("element_prefix_length"))
    val LINE_NUMBER = register(EventFields.Int("line_number"))
    val COLUMN_NUMBER = register(EventFields.Int("column_number"))
    val FILE_LINE_COUNT = register(EventFields.Int("file_line_count"))
    val SYMBOLS_IN_LINE_BEFORE_CARET = register(EventFields.Int("symbols_in_line_before_caret"))
    val SYMBOLS_IN_LINE_AFTER_CARET = register(EventFields.Int("symbols_in_line_after_caret"))
    val IS_WHITE_SPACE_BEFORE_CARET = register(EventFields.Boolean("is_white_space_before_caret"))
    val IS_WHITE_SPACE_AFTER_CARET = register(EventFields.Boolean("is_white_space_after_caret"))
    val NON_SPACE_SYMBOL_BEFORE_CARET = register(EventFields.Enum<CharCategory>("non_space_symbol_before_caret"))
    val NON_SPACE_SYMBOL_AFTER_CARET = register(EventFields.Enum<CharCategory>("non_space_symbol_after_caret"))
    val PREVIOUS_EMPTY_LINES_COUNT = register(EventFields.Int("previous_empty_lines_count"))
    val PREVIOUS_NON_EMPTY_LINE_LENGTH = register(EventFields.Int("previous_non_empty_line_length"))
    val FOLLOWING_EMPTY_LINES_COUNT = register(EventFields.Int("following_empty_lines_count"))
    val FOLLOWING_NON_EMPTY_LINE_LENGTH = register(EventFields.Int("following_non_empty_line_length"))
    val PARENT_FEATURES = listOf("first", "second", "third", "forth", "fifth").map {
      register(EventFields.Class("${it}_parent"))
    }
    val IMPORTS_COUNT = register(EventFields.Int("imports_count"))
    val POPULAR_LIBRARY_IMPORTS_COUNT = register(EventFields.Int("popular_library_imports_count"))
    val POPULAR_LIBRARY_IMPORTS_RATIO = register(EventFields.Float("popular_library_imports_ratio"))
    val LIBRARY_IMPORTS_COUNT = register(EventFields.Int("library_imports_count"))
    val LIBRARY_IMPORTS_RATIO = register(EventFields.Float("library_imports_ratio"))
    val NUMB_OF_PREV_QUALIFIERS = register(EventFields.Int("num_of_prev_qualifiers"))
    val PREV_NEIGHBOUR_KEYWORD_1 = register(EventFields.Int("prev_neighbour_keyword_1"))
    val PREV_NEIGHBOUR_KEYWORD_2 = register(EventFields.Int("prev_neighbour_keyword_2"))
    val PREV_SAME_LINE_KEYWORD_1 = register(EventFields.Int("prev_same_line_keyword_1"))
    val PREV_SAME_LINE_KEYWORD_2 = register(EventFields.Int("prev_same_line_keyword_2"))
    val PREV_SAME_COLUMN_KEYWORD_1 = register(EventFields.Int("prev_same_column_keyword_1"))
    val PREV_SAME_COLUMN_KEYWORD_2 = register(EventFields.Int("prev_same_column_keyword_2"))
    val IS_IN_ARGUMENTS = register(EventFields.Boolean("is_in_arguments"))
    val IS_DIRECTLY_IN_ARGUMENTS_CONTEXT = register(EventFields.Boolean("is_directly_in_arguments_context"))
    val ARGUMENT_INDEX = register(EventFields.Int("argument_index"))
    val ARGUMENTS_SIZE = register(EventFields.Int("number_of_arguments_already"))
    val HAVE_NAMED_ARG_LEFT = register(EventFields.Boolean("have_named_arg_left"))
    val HAVE_NAMED_ARG_RIGHT = register(EventFields.Boolean("have_named_arg_right"))
    val HAVE_OPENING_PARENTHESIS_LEFT = register(EventFields.Boolean("have_opening_parenthesis_left"))
    val HAVE_OPENING_BRACKET_LEFT = register(EventFields.Boolean("have_opening_bracket_left"))
    val HAVE_OPENING_BRACE_LEFT = register(EventFields.Boolean("have_opening_brace_left"))
    val HAVE_OPENING_ANGLE_BRACKET_LEFT = register(EventFields.Boolean("have_opening_angle_bracket_left"))
    val IS_IN_CONDITIONAL_STATEMENT = register(EventFields.Boolean("is_in_conditional_statement"))
    val IS_IN_FOR_STATEMENT = register(EventFields.Boolean("is_in_for_statement"))
    val BLOCK_STATEMENT_LEVEL = register(EventFields.Int("block_statement_level"))
    val TOPIC_TO_FIELD = listOf("data_science", "web")
      .associateWith { register(EventFields.Boolean("has_${it}_imports")) }
    val SCOPE_TYPE = scopeFeatures { EventFields.Enum<ScopeType>("${it}_scope_type", "Type of ${it} scope where the caret is placed") }
    val SCOPE_INIT = scopeFeatures { EventFields.Boolean("${it}_scope_init", "True if caret is placed at the ${it} scope initialization") }
    val SCOPE_NUM_LINES = scopeFeatures { EventFields.Int("${it}_scope_num_lines", "Number of lines in the ${it} scope where the caret is placed") }
    val SCOPE_NUM_SYMBOLS = scopeFeatures { EventFields.Int("${it}_scope_num_symbols", "Number of symbols in the ${it} scope where the caret is placed") }
    val SCOPE_LINE_OFFSET = scopeFeatures { EventFields.Int("${it}_scope_line_offset", "Line offset of the caret in the ${it} scope") }
    val SCOPE_OFFSET = scopeFeatures { EventFields.Int("${it}_scope_offset", "Char offset of the caret in the ${it} scope") }
    val SCOPE_VALUABLE_SYMBOLS_BEFORE = scopeFeatures { EventFields.Boolean("${it}_scope_valuable_symbols_before", "False if in the ${it} scope before caret there are only whitespaces or statements/strings enclosures") }
    val SCOPE_VALUABLE_SYMBOLS_AFTER = scopeFeatures { EventFields.Boolean("${it}_scope_valuable_symbols_after", "False if in the ${it} scope after caret there are only whitespaces or statements/strings enclosures") }
    val SCOPE_HAS_ERROR_PSI = scopeFeatures { EventFields.Boolean("${it}_scope_has_error_psi", "True if in the ${it} scope there's any PsiError element") }

    val TIME_SINCE_LAST_TYPING = register(EventFields.Long("time_since_last_typing", "Duration between current typing and previous one."))
    val TYPING_SPEEDS = TypingSpeedTracker.getEventFields().map {
      register(it)
    }

    private fun <T> scopeFeatures(createFeatureDeclaration: (String) -> EventField<T>): List<EventField<T>> {
      return listOf(
        register(createFeatureDeclaration("caret")),
        register(createFeatureDeclaration("parent")),
        register(createFeatureDeclaration("grand_parent")),
        register(createFeatureDeclaration("prev")),
        register(createFeatureDeclaration("next")),
      )
    }
  }

  internal class CollectorExtension : InlineCompletionSessionLogsEP {
    override val logGroups: List<PhasedLogs> = listOf(Logs)
  }
}
