// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EditorModificationUtilEx {
  public static void deleteSelectedText(Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if(!selectionModel.hasSelection()) return;

    int selectionStart = selectionModel.getSelectionStart();
    int selectionEnd = selectionModel.getSelectionEnd();

    VisualPosition selectionStartPosition = selectionModel.getSelectionStartPosition();
    if (editor.isColumnMode() && editor.getCaretModel().supportsMultipleCarets() && selectionStartPosition != null) {
      editor.getCaretModel().moveToVisualPosition(selectionStartPosition);
    }
    else {
      editor.getCaretModel().moveToOffset(selectionStart);
    }
    selectionModel.removeSelection();
    editor.getDocument().deleteString(selectionStart, selectionEnd);
    scrollToCaret(editor);
  }

  public static void insertStringAtCaret(Editor editor, @NotNull String s) {
    insertStringAtCaret(editor, s, false, true);
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode) {
    return insertStringAtCaret(editor, s, toProcessOverwriteMode, s.length());
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, boolean toMoveCaret) {
    return insertStringAtCaret(editor, s, toProcessOverwriteMode, toMoveCaret, s.length());
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, int caretShift) {
    return insertStringAtCaret(editor, s, toProcessOverwriteMode, true, caretShift);
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, boolean toMoveCaret, int caretShift) {
    int result = insertStringAtCaretNoScrolling(editor, s, toProcessOverwriteMode, toMoveCaret, caretShift);
    if (toMoveCaret) {
      scrollToCaret(editor);
    }
    return result;
  }

  protected static int insertStringAtCaretNoScrolling(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, boolean toMoveCaret, int caretShift) {
    // There is a possible case that particular soft wraps become hard wraps if the caret is located at soft wrap-introduced virtual
    // space, hence, we need to give editor a chance to react accordingly.
    editor.getSoftWrapModel().beforeDocumentChangeAtCaret();
    int oldOffset = editor.getSelectionModel().getSelectionStart();

    String filler = editor.getSelectionModel().hasSelection() ? "" : calcStringToFillVirtualSpace(editor);
    if (filler.length() > 0) {
      s = filler + s;
    }

    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    if (editor.isInsertMode() || !toProcessOverwriteMode) {
      if (selectionModel.hasSelection()) {
        document.replaceString(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), s);
      } else {
        document.insertString(oldOffset, s);
      }
    } else {
      deleteSelectedText(editor);
      if (s.length() == 1 && Character.isLowSurrogate(s.charAt(0)) &&
          oldOffset > 0 && Character.isHighSurrogate(document.getImmutableCharSequence().charAt(oldOffset - 1))) {
        // Hack to support input of surrogate pairs in editor via InputMethodEvent.
        // Such input is processed char-by-char using EditorImpl.processKeyTyped.
        document.insertString(oldOffset, s);
      }
      else {
        int lineNumber = editor.getCaretModel().getLogicalPosition().line;
        int lineEndOffset = document.getLineEndOffset(lineNumber);
        int inputCodePointCount = s.codePointCount(0, s.length());
        int endOffset;
        try {
          endOffset = Math.min(lineEndOffset,
                               Character.offsetByCodePoints(document.getImmutableCharSequence(), oldOffset, inputCodePointCount));
        }
        catch (IndexOutOfBoundsException e) {
          endOffset = lineEndOffset;
        }
        document.replaceString(oldOffset, endOffset, s);
      }
    }

    int offset = oldOffset + filler.length() + caretShift;
    if (toMoveCaret){
      editor.getCaretModel().moveToVisualPosition(editor.offsetToVisualPosition(offset, false, true));
      selectionModel.removeSelection();
    }
    else if (editor.getCaretModel().getOffset() != oldOffset) { // handling the case when caret model tracks document changes
      editor.getCaretModel().moveToOffset(oldOffset);
    }

    return offset;
  }

  public static String calcStringToFillVirtualSpace(Editor editor) {
    int afterLineEnd = calcAfterLineEnd(editor);
    if (afterLineEnd > 0) {
      return calcStringToFillVirtualSpace(editor, afterLineEnd);
    }

    return "";
  }

  public static String calcStringToFillVirtualSpace(Editor editor, int afterLineEnd) {
    final Project project = editor.getProject();
    StringBuilder buf = new StringBuilder();
    final Document doc = editor.getDocument();
    final int caretOffset = editor.getCaretModel().getOffset();
    boolean atLineStart = caretOffset >= doc.getTextLength() || doc.getLineStartOffset(doc.getLineNumber(caretOffset)) == caretOffset;
    if (atLineStart && project != null) {
      int offset = editor.getCaretModel().getOffset();
      PsiDocumentManager.getInstance(project).commitDocument(doc); // Sync document and PSI before formatting.
      String properIndent = offset >= doc.getTextLength() ? "" : CodeStyleFacade.getInstance(project).getLineIndent(doc, offset);
      if (properIndent != null) {
        int tabSize = editor.getSettings().getTabSize(project);
        for (int i = 0; i < properIndent.length(); i++) {
          if (properIndent.charAt(i) == ' ') {
            afterLineEnd--;
          }
          else if (properIndent.charAt(i) == '\t') {
            if (afterLineEnd < tabSize) {
              break;
            }
            afterLineEnd -= tabSize;
          }
          buf.append(properIndent.charAt(i));
          if (afterLineEnd == 0) break;
        }
      } else {
        EditorSettings editorSettings = editor.getSettings();
        boolean useTab = editorSettings.isUseTabCharacter(editor.getProject());
        if (useTab) {
          int tabSize = editorSettings.getTabSize(project);
          while (afterLineEnd >= tabSize) {
            buf.append('\t');
            afterLineEnd -= tabSize;
          }
        }
      }
    }

    for (int i = 0; i < afterLineEnd; i++) {
      buf.append(' ');
    }

    return buf.toString();
  }

  /**
   * This method is safe to run both in and out of {@link CaretModel#runForEachCaret(CaretAction)} context.
   * It scrolls to primary caret in both cases, and, in the former case, avoids performing excessive scrolling in case of large number
   * of carets.
   */
  public static void scrollToCaret(@NotNull Editor editor) {
    if (editor.getCaretModel().getCurrentCaret() == editor.getCaretModel().getPrimaryCaret()) {
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  /**
   * Calculates difference in columns between current editor caret position and end of the logical line fragment displayed
   * on a current visual line.
   *
   * @param editor    target editor
   * @return          difference in columns between current editor caret position and end of the logical line fragment displayed
   *                  on a current visual line
   */
  public static int calcAfterLineEnd(Editor editor) {
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition logicalPosition = caretModel.getLogicalPosition();
    int lineNumber = logicalPosition.line;
    int columnNumber = logicalPosition.column;
    if (lineNumber >= document.getLineCount()) {
      return columnNumber;
    }

    int caretOffset = caretModel.getOffset();
    int anchorLineEndOffset = document.getLineEndOffset(lineNumber);
    List<? extends SoftWrap> softWraps = editor.getSoftWrapModel().getSoftWrapsForLine(logicalPosition.line);
    for (SoftWrap softWrap : softWraps) {
      if (!editor.getSoftWrapModel().isVisible(softWrap)) {
        continue;
      }

      int softWrapOffset = softWrap.getStart();
      if (softWrapOffset == caretOffset) {
        // There are two possible situations:
        //     *) caret is located on a visual line before soft wrap-introduced line feed;
        //     *) caret is located on a visual line after soft wrap-introduced line feed;
        VisualPosition position = editor.offsetToVisualPosition(caretOffset - 1);
        VisualPosition visualCaret = caretModel.getVisualPosition();
        if (position.line == visualCaret.line) {
          return visualCaret.column - position.column - 1;
        }
      }
      if (softWrapOffset > caretOffset) {
        anchorLineEndOffset = softWrapOffset;
        break;
      }

      // Same offset corresponds to all soft wrap-introduced symbols, however, current method should behave differently in
      // situations when the caret is located just before the soft wrap and at the next visual line.
      if (softWrapOffset == caretOffset) {
        boolean visuallyBeforeSoftWrap = caretModel.getVisualPosition().line < editor.offsetToVisualPosition(caretOffset).line;
        if (visuallyBeforeSoftWrap) {
          anchorLineEndOffset = softWrapOffset;
          break;
        }
      }
    }

    int lineEndColumnNumber = editor.offsetToLogicalPosition(anchorLineEndOffset).column;
    return columnNumber - lineEndColumnNumber;
  }


}
