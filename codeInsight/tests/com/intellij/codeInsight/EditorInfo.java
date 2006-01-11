package com.intellij.codeInsight;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author cdr
 */
public class EditorInfo {
  public static final String CARET_MARKER = "<caret>";
  public static final String SELECTION_START_MARKER = "<selection>";
  public static final String SELECTION_END_MARKER = "</selection>";

  String newFileText = null;
  RangeMarker caretMarker = null;
  RangeMarker selStartMarker = null;
  RangeMarker selEndMarker = null;

  public EditorInfo(String fileText) {
    Document document = EditorFactory.getInstance().createDocument(fileText);

    int caretIndex = fileText.indexOf(CARET_MARKER);
    int selStartIndex = fileText.indexOf(SELECTION_START_MARKER);
    int selEndIndex = fileText.indexOf(SELECTION_END_MARKER);

    caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
    selStartMarker = selStartIndex >= 0 ? document.createRangeMarker(selStartIndex, selStartIndex) : null;
    selEndMarker = selEndIndex >= 0 ? document.createRangeMarker(selEndIndex, selEndIndex) : null;

    if (caretMarker != null) {
      document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
    }
    if (selStartMarker != null) {
      document.deleteString(selStartMarker.getStartOffset(), selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
    }
    if (selEndMarker != null) {
      document.deleteString(selEndMarker.getStartOffset(), selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
    }

    newFileText = document.getText();
  }

  public String getNewFileText() {
    return newFileText;
  }

  public void applyToEditor(Editor editor) {
    if (caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(newFileText, caretMarker.getStartOffset());
      int caretCol = caretMarker.getStartOffset() - StringUtil.lineColToOffset(newFileText, caretLine, 0);
      LogicalPosition pos = new LogicalPosition(caretLine, caretCol);
      editor.getCaretModel().moveToLogicalPosition(pos);
    }

    if (selStartMarker != null) {
      editor.getSelectionModel().setSelection(selStartMarker.getStartOffset(), selEndMarker.getStartOffset());
    }
  }
}
