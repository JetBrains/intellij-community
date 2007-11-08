/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 6, 2002
 * Time: 4:54:58 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

import java.awt.*;

public class EditorActionUtil {
  protected static final Object EDIT_COMMAND_GROUP = Key.create("EditGroup");
  protected static final Object DELETE_COMMAND_GROUP = Key.create("DeleteGroup");

  public static void scrollRelatively(Editor editor, int lineShift) {
    if (lineShift != 0) {
      editor.getScrollingModel().scrollVertically(
        editor.getScrollingModel().getVerticalScrollOffset() + lineShift * editor.getLineHeight()
      );
    }

    Rectangle viewRectangle = editor.getScrollingModel().getVisibleArea();
    int lineNumber = editor.getCaretModel().getVisualPosition().line;
    if (viewRectangle != null) {
      VisualPosition startPos = editor.xyToVisualPosition(new Point(0, viewRectangle.y));
      int start = startPos.line + 1;
      VisualPosition endPos = editor.xyToVisualPosition(new Point(0, viewRectangle.y + viewRectangle.height));
      int end = endPos.line - 2;
      if (lineNumber < start) {
        editor.getCaretModel().moveCaretRelatively(0, start - lineNumber, false, false, true);
      }
      else if (lineNumber > end) {
        editor.getCaretModel().moveCaretRelatively(0, end - lineNumber, false, false, true);
      }
    }
  }

  public static void moveCaretRelativelyAndScroll(Editor editor,
                                                  int columnShift,
                                                  int lineShift,
                                                  boolean withSelection) {
    Rectangle viewRect = editor.getScrollingModel().getVisibleArea();
    VisualPosition pos = editor.getCaretModel().getVisualPosition();
    Point caretLocation = editor.visualPositionToXY(pos);
    int caretVShift = caretLocation.y - viewRect.y;

    editor.getCaretModel().moveCaretRelatively(columnShift, lineShift, withSelection, false, false);

    //editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    VisualPosition caretPos = editor.getCaretModel().getVisualPosition();
    Point caretLocation2 = editor.visualPositionToXY(caretPos);
    editor.getScrollingModel().scrollVertically(caretLocation2.y - caretVShift);
  }

  public static void indentLine(Project project, Editor editor, int lineNumber, int indent) {
    EditorSettings editorSettings = editor.getSettings();
    Document document = editor.getDocument();
    int spacesEnd = 0;
    int lineStart = 0;
    if (lineNumber < document.getLineCount()) {
      lineStart = document.getLineStartOffset(lineNumber);
      int lineEnd = document.getLineEndOffset(lineNumber);
      spacesEnd = lineStart;
      CharSequence text = document.getCharsSequence();
      for (; spacesEnd <= lineEnd; spacesEnd++) {
        if (spacesEnd == lineEnd) {
          break;
        }
        char c = text.charAt(spacesEnd);
        if (c != '\t' && c != ' ') {
          break;
        }
      }
    }
    int oldLength = editor.offsetToLogicalPosition(spacesEnd).column;

    int newLength = oldLength + indent;
    if (newLength < 0) {
      newLength = 0;
    }
    StringBuffer buf = new StringBuffer(newLength);
    int tabSize = editorSettings.getTabSize(project);
    for (int i = 0; i < newLength;) {
      if (tabSize > 0 && editorSettings.isUseTabCharacter(project) && i + tabSize <= newLength) {
        buf.append('\t');
        i += tabSize;
      }
      else {
        buf.append(' ');
        i++;
      }
    }

    int newCaretOffset = editor.getCaretModel().getOffset();
    if (newCaretOffset >= spacesEnd) {
      newCaretOffset += buf.length() - (spacesEnd - lineStart);
    }

    if (buf.length() > 0) {
      if (spacesEnd > lineStart) {
        document.replaceString(lineStart, spacesEnd, buf.toString());
      }
      else {
        document.insertString(lineStart, buf.toString());
      }
    }
    else {
      if (spacesEnd > lineStart) {
        document.deleteString(lineStart, spacesEnd);
      }
    }

    editor.getCaretModel().moveToOffset(newCaretOffset);
  }

  public static boolean isWordStart(CharSequence text, int offset, boolean isCamel) {
    char prev = offset > 0 ? text.charAt(offset - 1) : 0;
    char current = text.charAt(offset);
    char next = offset + 1 < text.length() ? text.charAt(offset + 1) : 0;

    final boolean firstIsIdentifierPart = Character.isJavaIdentifierPart(prev);
    final boolean secondIsIdentifierPart = Character.isJavaIdentifierPart(current);
    if (!firstIsIdentifierPart && secondIsIdentifierPart) {
      return true;
    }

    if (isCamel) {
      if (firstIsIdentifierPart && secondIsIdentifierPart &&
          (Character.isLowerCase(prev) && Character.isUpperCase(current) ||
           prev == '_' && current != '_' ||
           Character.isUpperCase(prev) && Character.isUpperCase(current) && Character.isLowerCase(next))) {
        return true;
      }
    }

    return (Character.isWhitespace(prev) || firstIsIdentifierPart) &&
           !Character.isWhitespace(current) && !secondIsIdentifierPart;
  }

  public static boolean isWordEnd(CharSequence text, int offset, boolean isCamel) {
    char prev = offset > 0 ? text.charAt(offset - 1) : 0;
    char current = text.charAt(offset);
    char next = offset + 1 < text.length() ? text.charAt(offset + 1) : 0;

    final boolean firstIsIdentifiePart = Character.isJavaIdentifierPart(prev);
    final boolean secondIsIdentifierPart = Character.isJavaIdentifierPart(current);
    if (firstIsIdentifiePart && !secondIsIdentifierPart) {
      return true;
    }

    if (isCamel) {
      if (firstIsIdentifiePart && secondIsIdentifierPart &&
          (Character.isLowerCase(prev) && Character.isUpperCase(current) || prev != '_' && current == '_' ||
          Character.isUpperCase(prev) && Character.isUpperCase(current) && Character.isLowerCase(next))) {
        return true;
      }
    }

    return !Character.isWhitespace(prev) && !firstIsIdentifiePart &&
           (Character.isWhitespace(current) || secondIsIdentifierPart);
  }

  public static void moveCaretToLineStart(Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();

    int columnNumber = editor.getCaretModel().getLogicalPosition().column;
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;

    VisualPosition visCaret = editor.getCaretModel().getVisualPosition();
    while (lineNumber >= 0 && editor.logicalToVisualPosition(new LogicalPosition(lineNumber, 0)).line == visCaret.line) lineNumber--;
    lineNumber++;

    EditorSettings editorSettings = editor.getSettings();
    if (!editorSettings.isSmartHome()) {
      LogicalPosition pos = new LogicalPosition(lineNumber, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);

      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return;
    }

    int offset = editor.getCaretModel().getOffset();
    if (lineNumber >= document.getLineCount() || offset >= document.getTextLength()) {
      int newColNumber = 0;
      if (columnNumber == 0) {
        newColNumber = findSmartIndent(editor, offset);
      }
      LogicalPosition pos = new LogicalPosition(lineNumber, newColNumber);
      editor.getCaretModel().moveToLogicalPosition(pos);

      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return;
    }

    int start = findFirstNonspaceOffsetOnTheLine(document, lineNumber);
    int lineEnd = document.getLineEndOffset(lineNumber);
    if (lineNumber > 0 && lineEnd == start && columnNumber == 0) {
      int newColNumber = findSmartIndent(editor, offset);
      LogicalPosition pos = new LogicalPosition(lineNumber, newColNumber);
      editor.getCaretModel().moveToLogicalPosition(pos);
      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return;
    }

    int lineStart = document.getLineStartOffset(lineNumber);
    int newOffset = lineStart;
    if (start < offset || columnNumber == 0) {
      newOffset = start;
    }
    editor.getCaretModel().moveToOffset(newOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  private static int findSmartIndent(Editor editor, int offset) {
    int nonSpaceLineNumber = findFirstNonspaceLineBack(editor.getDocument(), offset);
    if (nonSpaceLineNumber >= 0) {
      int newOffset = findFirstNonspaceOffsetOnTheLine(editor.getDocument(), nonSpaceLineNumber);
      return editor.offsetToLogicalPosition(newOffset).column;
    }
    return 0;
  }

  private static int findFirstNonspaceLineBack(Document document, int offset) {
    CharSequence text = document.getCharsSequence();
    int foundOffset = offset - 1;
    for (; foundOffset > 0; foundOffset--) {
      char c = text.charAt(foundOffset);
      if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
        break;
      }
    }
    if (foundOffset == -1) {
      return -1;
    }
    else {
      return document.getLineNumber(foundOffset);
    }
  }

  private static int findFirstNonspaceOffsetOnTheLine(Document document, int lineNumber) {
    int lineStart = document.getLineStartOffset(lineNumber);
    int lineEnd = document.getLineEndOffset(lineNumber);
    CharSequence text = document.getCharsSequence();
    int start = lineStart;
    for (; start < lineEnd; start++) {
      char c = text.charAt(start);
      if (c != ' ' && c != '\t') {
        return start;
      }
    }
    return lineEnd;
  }

  public static void moveCaretToLineEnd(Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();

    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    if (lineNumber >= document.getLineCount()) {
      LogicalPosition pos = new LogicalPosition(lineNumber, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);
      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return;
    }
    VisualPosition visCaret = editor.getCaretModel().getVisualPosition();
    visCaret = new VisualPosition(visCaret.line, EditorUtil.getLastVisualLineColumnNumber(editor, visCaret.line));

    LogicalPosition logLineEnd = editor.visualToLogicalPosition(visCaret);
    int offset = editor.logicalPositionToOffset(logLineEnd);
    lineNumber = logLineEnd.line;
    int newOffset = offset;

    CharSequence text = document.getCharsSequence();
    for (int i = newOffset - 1; i >= document.getLineStartOffset(lineNumber); i--) {
      if (text.charAt(i) != ' ' && text.charAt(i) != '\t') {
        break;
      }
      newOffset = i;
    }

    if (newOffset == editor.getCaretModel().getOffset()) {
      newOffset = offset;
    }

    editor.getCaretModel().moveToOffset(newOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretToNextWord(Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();

    int offset = caretModel.getOffset();
    CharSequence text = document.getCharsSequence();
    if (offset == document.getTextLength() - 1) {
      return;
    }
    int newOffset = offset + 1;
    int lineNumber = caretModel.getLogicalPosition().line;
    if (lineNumber >= document.getLineCount()) return;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if (newOffset > maxOffset) {
      if (lineNumber + 1 >= document.getLineCount()) {
        return;
      }
      maxOffset = document.getLineEndOffset(lineNumber + 1);
    }
    boolean camel = editor.getSettings().isCamelWords();
    for (; newOffset < maxOffset; newOffset++) {
      if (isWordStart(text, newOffset, camel)) {
        break;
      }
    }
    caretModel.moveToOffset(newOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  private static void setupSelection(Editor editor,
                                     boolean isWithSelection,
                                     int selectionStart, LogicalPosition blockSelectionStart) {
    if (isWithSelection) {
      if (editor.isColumnMode()) {
        editor.getSelectionModel().setBlockSelection(blockSelectionStart, editor.getCaretModel().getLogicalPosition());
      }
      else {
        editor.getSelectionModel().setSelection(selectionStart, editor.getCaretModel().getOffset());
      }
    }
    else {
      editor.getSelectionModel().removeSelection();
    }
  }

  public static void moveCaretToPreviousWord(Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();

    int offset = editor.getCaretModel().getOffset();
    if (offset == 0) return;

    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    CharSequence text = document.getCharsSequence();
    int newOffset = offset - 1;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
    boolean camel = editor.getSettings().isCamelWords();
    for (; newOffset > minOffset; newOffset--) {
      if (isWordStart(text, newOffset, camel)) break;
    }
    editor.getCaretModel().moveToOffset(newOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageUp(Editor editor, boolean isWithSelection) {
    ((EditorEx)editor).stopOptimizedScrolling();
    int lineHeight = editor.getLineHeight();
    Rectangle viewRect = editor.getScrollingModel().getVisibleArea();
    int linesIncrement = viewRect.height / lineHeight;
    editor.getScrollingModel().scrollVertically(viewRect.y - viewRect.y % lineHeight - linesIncrement * lineHeight);
    int lineShift = -linesIncrement;
    editor.getCaretModel().moveCaretRelatively(0, lineShift, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretPageDown(Editor editor, boolean isWithSelection) {
    ((EditorEx)editor).stopOptimizedScrolling();
    int lineHeight = editor.getLineHeight();
    Rectangle viewRect = editor.getScrollingModel().getVisibleArea();
    int linesIncrement = viewRect.height / lineHeight;
    int allowedBottom = ((EditorEx)editor).getContentSize().height - viewRect.height;
    editor.getScrollingModel().scrollVertically(
      Math.min(allowedBottom, viewRect.y - viewRect.y % lineHeight + linesIncrement * lineHeight));
    editor.getCaretModel().moveCaretRelatively(0, linesIncrement, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretPageTop(Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();
    Rectangle viewRect = editor.getScrollingModel().getVisibleArea();
    int lineNumber = viewRect.y / lineHeight;
    if (viewRect.y % lineHeight > 0) {
      lineNumber++;
    }
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageBottom(Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();
    Rectangle viewRect = editor.getScrollingModel().getVisibleArea();
    int lineNumber = (viewRect.y + viewRect.height) / lineHeight - 1;
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }
}
