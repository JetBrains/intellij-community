// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.hint.EditorFragmentComponent
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.HighlighterIteratorWrapper
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPlainTextFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.tree.ILazyParseableElementType
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.LightweightHint
import com.intellij.util.Alarm
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.CharArrayUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.function.IntUnaryOperator
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

private val BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY = Key.create<MutableList<RangeHighlighter>>("BraceHighlighter.BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY")
private val LINE_MARKER_IN_EDITOR_KEY = Key.create<RangeHighlighter>("BraceHighlighter.LINE_MARKER_IN_EDITOR_KEY")
private val HINT_IN_EDITOR_KEY = Key.create<LightweightHint>("BraceHighlighter.HINT_IN_EDITOR_KEY")

class BraceHighlightingHandler internal constructor(
  private val project: Project,
  private val editor: Editor,
  private val alarm: Alarm,
  private val psiFile: PsiFile,
) {
  private val document = editor.document
  private val codeInsightSettings = CodeInsightSettings.getInstance()

  companion object {
    const val LAYER: Int = HighlighterLayer.LAST + 1

    @JvmStatic
    fun getLazyParsableHighlighterIfAny(project: Project, editor: Editor, psiFile: PsiFile): EditorHighlighter {
      if (!PsiDocumentManager.getInstance(project).isCommitted(editor.document)) {
        return editor.highlighter
      }

      val elementAt = psiFile.findElementAt(editor.caretModel.offset)
      for (e in SyntaxTraverser.psiApi().parents(elementAt).takeWhile(Conditions.notEqualTo(psiFile))) {
        if (PsiUtilCore.getElementType(e) !is ILazyParseableElementType) {
          continue
        }

        val language = ILazyParseableElementType.LANGUAGE_KEY.get(e.node) ?: continue
        val range = e.textRange
        val offset = range.startOffset
        val syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, psiFile.virtualFile)
        val highlighter = object : LexerEditorHighlighter(syntaxHighlighter, editor.colorsScheme) {
          override fun createIterator(startOffset: Int): HighlighterIterator {
            return object : HighlighterIteratorWrapper(super.createIterator(max(startOffset - offset, 0))) {
              override fun getStart(): Int = super.getStart() + offset

              override fun getEnd(): Int = super.getEnd() + offset
            }
          }
        }
        highlighter.setText(editor.document.getText(range))
        return highlighter
      }
      return editor.highlighter
    }

    @JvmStatic
    @RequiresEdt
    fun showScopeHint(
      editor: Editor,
      psiFile: PsiFile,
      leftBraceStart: Int,
      leftBraceEnd: Int,
    ) {
      BraceHighlightingHandler(psiFile.project, editor, service<BackgroundHighlighter>().alarm, psiFile)
        .showScopeHint(leftBraceStart, leftBraceEnd, null)
    }

    /**
     * Draws a vertical line on the gutter of `editor`, covering lines of the `document` from the `startLine` to the
     * `endLine`
     */
    @JvmStatic
    @RequiresEdt
    fun lineMarkFragment(editor: EditorEx, document: Document, startLine: Int, endLine: Int, matched: Boolean) {
      removeLineMarkers(editor)

      if (startLine >= endLine || endLine >= document.lineCount) {
        return
      }

      val startOffset = document.getLineStartOffset(startLine)
      val endOffset = document.getLineEndOffset(endLine)

      val renderer = createLineMarkerRenderer(matched)

      val highlighter = editor.markupModel
        .addRangeHighlighterAndChangeAttributes(
          null,
          startOffset,
          endOffset,
          0,
          HighlighterTargetArea.LINES_IN_RANGE,
          false,
        ) { it.lineMarkerRenderer = renderer }
      editor.putUserData(LINE_MARKER_IN_EDITOR_KEY, highlighter)
    }

    fun createLineMarkerRenderer(matched: Boolean): LineMarkerRenderer {
      val key = if (matched) CodeInsightColors.MATCHED_BRACE_ATTRIBUTES else CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES
      return DefaultLineMarkerRenderer(key, 1, 0, LineMarkerRendererEx.Position.RIGHT, true)
    }

    private fun getHighlightersList(editor: Editor): MutableList<RangeHighlighter> {
      // braces are highlighted across the whole editor, not in each injected editor separately
      val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
      var highlighters = hostEditor.getUserData(BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY)
      if (highlighters == null) {
        highlighters = ArrayList()
        hostEditor.putUserData(BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY, highlighters)
      }
      return highlighters
    }
    @ApiStatus.Internal
    @RequiresEdt
    fun clearBraceHighlighters(editor: Editor) {
      val highlighters = getHighlightersList(editor)
      for (highlighter in highlighters) {
        highlighter.dispose()
      }
      highlighters.clear()

      editor.getUserData(HINT_IN_EDITOR_KEY)?.let { hint ->
        hint.hide()
        editor.putUserData(HINT_IN_EDITOR_KEY, null)
      }
      if (editor is EditorEx) {
        removeLineMarkers(editor)
      }
    }
  }

  @RequiresEdt
  fun updateBraces() {
    ThreadingAssertions.assertEventDispatchThread()

    clearBraceHighlighters(editor)

    if (!BackgroundHighlightingUtil.needMatching(editor, codeInsightSettings)) {
      return
    }

    var offset = editor.caretModel.offset
    val chars = editor.document.charsSequence

    var context = SlowOperations.knownIssue("IJPL-162400").use {
      BraceMatchingUtil.computeHighlightingAndNavigationContext(editor, psiFile)
    }
    if (context != null) {
      doHighlight(context.currentBraceOffset, context.isCaretAfterBrace)
      offset = context.currentBraceOffset
    }
    else if (offset > 0 && offset < chars.length) {
      // There is a possible case that there are paired braces nearby the caret position and the document contains only white
      // space symbols between them. We want to highlight such braces as well.
      // Example:
      //     public void test() { <caret>
      //     }
      val c = chars[offset]
      var searchForward = c != '\n'

      // Try to find matched brace backwards.
      val backwardNonSpaceEndOffset = CharArrayUtil.shiftBackward(chars, offset - 1, "\t ") + 1
      if (backwardNonSpaceEndOffset in 1..<offset) {
        context = SlowOperations.knownIssue("IJPL-162400").use {
          BraceMatchingUtil.computeHighlightingAndNavigationContext(editor, psiFile, backwardNonSpaceEndOffset)
        }
        if (context != null) {
          doHighlight(context.currentBraceOffset, true)
          offset = context.currentBraceOffset
          searchForward = false
        }
      }

      // try to find matched brace forward.
      if (searchForward) {
        val nextNonSpaceCharOffset = CharArrayUtil.shiftForward(chars, offset, "\t ")
        if (nextNonSpaceCharOffset > offset) {
          context = BraceMatchingUtil.computeHighlightingAndNavigationContext(editor, psiFile, nextNonSpaceCharOffset)
          if (context != null) {
            doHighlight(context.currentBraceOffset, true)
            offset = context.currentBraceOffset
          }
        }
      }
    }

    if (codeInsightSettings.HIGHLIGHT_SCOPE) {
      SlowOperations.knownIssue("IJPL-162400").use {
        highlightScope(offset)
      }
    }
  }

  private fun getFileTypeByOffset(offset: Int): FileType {
    return PsiUtilBase.getPsiFileAtOffset(psiFile, offset).fileType
  }

  private val editorHighlighter: EditorHighlighter
    get() = getLazyParsableHighlighterIfAny(project, editor, psiFile)

  @RequiresEdt
  private fun highlightScope(offset: Int) {
    if (!psiFile.isValid) {
      return
    }
    if (editor.foldingModel.isOffsetCollapsed(offset)) {
      return
    }
    if (editor.document.textLength <= offset) {
      return
    }

    val iterator = editorHighlighter.createIterator(offset)
    val chars = document.charsSequence

    val fileType = getFileTypeByOffset(offset)

    if (!(BraceMatchingUtil.isStructuralBraceToken(fileType, iterator, chars) &&
          (BraceMatchingUtil.isRBraceToken(iterator, chars, fileType) ||
           BraceMatchingUtil.isLBraceToken(iterator, chars, fileType))) &&
        BraceMatchingUtil.findStructuralLeftBrace(fileType, iterator, chars)
    ) {
      highlightLeftBrace(iterator, true, fileType)
    }
  }

  /**
   * Highlighting braces at `offset`
   *
   * @param isAdjustedPosition true mean s that `offset` been adjusted, e.g. spaces been skipped before or after caret position
   */
  @RequiresEdt
  private fun doHighlight(offset: Int, isAdjustedPosition: Boolean) {
    if (editor.foldingModel.isOffsetCollapsed(offset)) {
      return
    }

    var iterator = editorHighlighter.createIterator(offset)
    val chars = document.charsSequence
    val fileType = getFileTypeByOffset(offset)
    if (BraceMatchingUtil.isLBraceToken(iterator, chars, fileType)) {
      highlightLeftBrace(iterator, false, fileType)

      if (offset > 0 && !isAdjustedPosition && !editor.settings.isBlockCursor) {
        iterator = editorHighlighter.createIterator(offset - 1)
        if (BraceMatchingUtil.isRBraceToken(iterator, chars, fileType)) {
          highlightRightBrace(iterator, fileType)
        }
      }
    }
    else if (BraceMatchingUtil.isRBraceToken(iterator, chars, fileType)) {
      highlightRightBrace(iterator, fileType)
    }
  }

  @RequiresEdt
  private fun highlightRightBrace(iterator: HighlighterIterator, fileType: FileType) {
    val brace1 = TextRange.create(iterator.start, iterator.end)
    val matched = BraceMatchingUtil.matchBrace(document.charsSequence, fileType, iterator, false)
    val brace2 = if (iterator.atEnd()) null else TextRange.create(iterator.start, iterator.end)
    highlightBraces(brace2, brace1, matched, false, fileType)
  }

  @RequiresEdt
  private fun highlightLeftBrace(iterator: HighlighterIterator, scopeHighlighting: Boolean, fileType: FileType) {
    val brace1Start = TextRange.create(iterator.start, iterator.end)
    val matched = BraceMatchingUtil.matchBrace(document.charsSequence, fileType, iterator, true)

    val brace2End = if (iterator.atEnd()) null else TextRange.create(iterator.start, iterator.end)
    highlightBraces(brace1Start, brace2End, matched, scopeHighlighting, fileType)
  }

  @RequiresEdt
  fun highlightBraces(lBrace: TextRange?, rBrace: TextRange?, matched: Boolean, scopeHighlighting: Boolean, fileType: FileType) {
    if (!matched && fileType === FileTypes.PLAIN_TEXT) {
      return
    }

    if (rBrace != null && !scopeHighlighting) {
      highlightBrace(rBrace, matched)
    }

    if (lBrace != null && !scopeHighlighting) {
      highlightBrace(lBrace, matched)
    }

    // null in default project
    val fileEditorManager = FileEditorManager.getInstance(project) ?: return
    if (!fileEditorManager.selectedTextEditorWithRemotes.any { it == editor }) {
      return
    }

    if (lBrace != null && rBrace != null) {
      val startLine = editor.offsetToLogicalPosition(lBrace.startOffset).line
      val endLine = editor.offsetToLogicalPosition(rBrace.endOffset).line
      if (endLine - startLine > 0 && editor is EditorEx) {
        lineMarkFragment(editor, document, startLine, endLine, matched)
      }

      if (!scopeHighlighting) {
        showScopeHint(lBrace.startOffset, lBrace.endOffset)
      }
    }
  }

  @RequiresEdt
  private fun highlightBrace(braceRange: TextRange, matched: Boolean) {
    val attributesKey = if (matched) CodeInsightColors.MATCHED_BRACE_ATTRIBUTES else CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES
    val rbraceHighlighter = editor.markupModel
      .addRangeHighlighter(attributesKey, braceRange.startOffset, braceRange.endOffset, LAYER, HighlighterTargetArea.EXACT_RANGE)
    rbraceHighlighter.isGreedyToLeft = false
    rbraceHighlighter.isGreedyToRight = false
    registerHighlighter(rbraceHighlighter)
  }

  private fun registerHighlighter(highlighter: RangeHighlighter) {
    getHighlightersList(editor).add(highlighter)
  }

  @RequiresEdt
  private fun showScopeHint(lbraceStart: Int, lbraceEnd: Int) {
    showScopeHint(lbraceStart, lbraceEnd) { offset ->
      if (psiFile !is PsiPlainTextFile && psiFile.isValid) {
        BraceMatchingUtil.getBraceMatcher(
          getFileTypeByOffset(offset),
          PsiUtilCore.getLanguageAtOffset(psiFile, offset),
        ).getCodeConstructStart(psiFile, offset)
      }
      else {
        offset
      }
    }
  }

  /**
   * Schedules with [.alarm] displaying of the scope start in the `editor`
   *
   * @param startComputation optional adjuster for the brace start offset
   */
  @RequiresEdt
  private fun showScopeHint(leftBraceStart: Int, leftBraceEnd: Int, startComputation: IntUnaryOperator?) {
    val editor = editor
    val project = editor.project ?: return
    val bracePosition = editor.offsetToLogicalPosition(leftBraceStart)
    val braceLocation = editor.logicalPositionToXY(bracePosition)
    val y = braceLocation.y
    val modalityState = ModalityState.stateForComponent(editor.component).asContextElement()
    val clientId = ClientId.currentOrNull?.asContextElement() ?: EmptyCoroutineContext
    alarm.schedule {
      delay(300.milliseconds)
      val psiDocumentManager = project.serviceAsync<PsiDocumentManager>()
      withContext(Dispatchers.EDT + modalityState + clientId) {
        // yes, despite readAction, we must execute in EDT, see performLaterWhenAllCommitted implementation
        readAction {
          psiDocumentManager.performLaterWhenAllCommitted {
            if (editor.isDisposed || !editor.component.isShowing) {
              return@performLaterWhenAllCommitted
            }

            val viewRect = editor.scrollingModel.visibleArea
            if (y >= viewRect.y) {
              return@performLaterWhenAllCommitted
            }

            var range = TextRange(startComputation?.applyAsInt(leftBraceStart) ?: leftBraceStart, leftBraceEnd)
            val document = editor.document
            var line1 = document.getLineNumber(range.startOffset)
            val line2 = document.getLineNumber(range.endOffset)
            if (editor is EditorImpl && editor.shouldSuppressEditorFragmentHint(line1)) {
              return@performLaterWhenAllCommitted
            }

            line1 = max(
              line1.toDouble(),
              (line2 - EditorFragmentComponent.getAvailableVisualLinesAboveEditor(editor) + 1).toDouble(),
            ).toInt()
            range = TextRange(document.getLineStartOffset(line1), range.endOffset)
            editor.putUserData(HINT_IN_EDITOR_KEY, EditorFragmentComponent.showEditorFragmentHint(editor, range, true, true))
          }
        }
      }
    }
  }
}

@RequiresEdt
private fun removeLineMarkers(editor: EditorEx) {
  ThreadingAssertions.assertEventDispatchThread()
  val marker = editor.getUserData(LINE_MARKER_IN_EDITOR_KEY)
  if (marker != null && editor.markupModel.containsHighlighter(marker)) {
    marker.dispose()
  }
  editor.putUserData(LINE_MARKER_IN_EDITOR_KEY, null)
}
