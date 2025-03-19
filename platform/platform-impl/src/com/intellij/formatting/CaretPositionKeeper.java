// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.MathUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * There is a possible case that cursor is located at the end of the line that contains only white spaces. For example:
 * public void foo() {
 * <caret>
 * }
 * Formatter removes such white spaces, i.e. keeps only line feed symbol. But we want to preserve caret position then.
 * So, if 'virtual space in editor' is enabled, we save target visual column. Caret indent is ensured otherwise
 */
@ApiStatus.Internal
public final class CaretPositionKeeper {

  Editor myEditor;
  Document myDocument;
  CaretModel myCaretModel;
  RangeMarker myBeforeCaretRangeMarker;
  String myCaretIndentToRestore;
  int myVisualColumnToRestore = -1;
  boolean myBlankLineIndentPreserved;

  CaretPositionKeeper(@NotNull Editor editor, @NotNull CodeStyleSettings settings, @NotNull Language language) {
    myEditor = editor;
    myCaretModel = editor.getCaretModel();
    myDocument = editor.getDocument();
    myBlankLineIndentPreserved = isBlankLineIndentPreserved(settings, language);

    Project project = myEditor.getProject();
    if (project != null) {
      PsiDocumentManager.getInstance(project).commitDocument(myDocument);
    }

    int caretOffset = getCaretOffset();
    CaretRestorationDecider decider = CaretRestorationDecider.forLanguage(language);

    if (decider == null) {
      decider = DefaultCaretRestorationDecider.INSTANCE;
    }

    boolean shouldFixCaretPosition = decider.shouldRestoreCaret(myDocument, myEditor, caretOffset);

    if (shouldFixCaretPosition) {
      initRestoreInfo(caretOffset);
    }
  }

  private static boolean isBlankLineIndentPreserved(@NotNull CodeStyleSettings settings, @NotNull Language language) {
    CommonCodeStyleSettings langSettings = settings.getCommonSettings(language);
    CommonCodeStyleSettings.IndentOptions indentOptions = langSettings.getIndentOptions();
    return indentOptions != null && indentOptions.KEEP_INDENTS_ON_EMPTY_LINES;
  }

  private void initRestoreInfo(int caretOffset) {
    int lineStartOffset = getLineStartOffsetByTotalOffset(myDocument, caretOffset);

    myVisualColumnToRestore = myCaretModel.getVisualPosition().column;
    myCaretIndentToRestore = myDocument.getText(TextRange.create(lineStartOffset, caretOffset));
    myBeforeCaretRangeMarker = myDocument.createRangeMarker(0, lineStartOffset);
  }

  public void restoreCaretPosition() {
    if (isVirtualSpaceEnabled()) {
      restoreVisualPosition();
    }
    else {
      restorePositionByIndentInsertion();
    }
  }

  private void restorePositionByIndentInsertion() {
    if (myBeforeCaretRangeMarker == null ||
        !myBeforeCaretRangeMarker.isValid() ||
        myCaretIndentToRestore == null ||
        myBlankLineIndentPreserved) {
      return;
    }
    int newCaretLineStartOffset = myBeforeCaretRangeMarker.getEndOffset();
    myBeforeCaretRangeMarker.dispose();
    if (myCaretModel.getVisualPosition().column == myVisualColumnToRestore) {
      return;
    }
    Project project = myEditor.getProject();
    if (project == null || PsiDocumentManager.getInstance(project).isDocumentBlockedByPsi(myDocument)) {
      return;
    }
    insertWhiteSpaceIndentIfNeeded(newCaretLineStartOffset);
  }

  private void restoreVisualPosition() {
    if (myVisualColumnToRestore < 0) {
      EditorUtil.runWithAnimationDisabled(myEditor, () -> myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE));
      return;
    }
    VisualPosition position = myCaretModel.getVisualPosition();
    if (myVisualColumnToRestore != position.column) {
      myCaretModel.moveToVisualPosition(new VisualPosition(position.line, myVisualColumnToRestore));
    }
  }

  private void insertWhiteSpaceIndentIfNeeded(int caretLineOffset) {
    int lineToInsertIndent = myDocument.getLineNumber(caretLineOffset);
    if (!lineContainsWhiteSpaceSymbolsOnly(lineToInsertIndent)) {
      return;
    }

    int lineToInsertStartOffset = myDocument.getLineStartOffset(lineToInsertIndent);

    if (lineToInsertIndent != getCurrentCaretLine()) {
      myCaretModel.moveToOffset(lineToInsertStartOffset);
    }
    myDocument.replaceString(lineToInsertStartOffset, caretLineOffset, myCaretIndentToRestore);
  }


  private boolean isVirtualSpaceEnabled() {
    return myEditor.getSettings().isVirtualSpace();
  }

  private int getCaretOffset() {
    int caretOffset = myCaretModel.getOffset();
    int upperBound = Math.max(myDocument.getTextLength() - 1, 0);
    caretOffset = MathUtil.clamp(caretOffset, 0, upperBound);
    return caretOffset;
  }

  private boolean lineContainsWhiteSpaceSymbolsOnly(int lineNumber) {
    int startOffset = myDocument.getLineStartOffset(lineNumber);
    int endOffset = myDocument.getLineEndOffset(lineNumber);
    return CharArrayUtil.isEmptyOrSpaces(myDocument.getCharsSequence(), startOffset, endOffset);
  }

  private int getCurrentCaretLine() {
    return myDocument.getLineNumber(myCaretModel.getOffset());
  }

  static int getLineStartOffsetByTotalOffset(Document document, int offset) {
    int line = document.getLineNumber(offset);
    return document.getLineStartOffset(line);
  }

  static int getLineEndOffsetByTotalOffset(Document document, int offset) {
    int line = document.getLineNumber(offset);
    return document.getLineEndOffset(line);
  }
}