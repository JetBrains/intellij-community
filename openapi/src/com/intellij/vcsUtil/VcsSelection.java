package com.intellij.vcsUtil;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;

public class VcsSelection {
  private final Document myDocument;
  private final int mySelectionStartLineNumber;
  private final int mySelectionEndLineNumber;
  private final String mySelectedAreaName;

  public VcsSelection(Document document, SelectionModel selectionModel) {
    this(document,
         new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()),
         "Selection");
  }

  public VcsSelection(Document document, TextRange textRange, String selectedAreaName) {
    myDocument = document;
    int startOffset = textRange.getStartOffset();
    mySelectionStartLineNumber = document.getLineNumber(startOffset);
    int endOffset = textRange.getEndOffset();
    mySelectionEndLineNumber = endOffset >= document.getTextLength() ? document.getLineCount() : document.getLineNumber(endOffset);
    mySelectedAreaName = selectedAreaName;
  }

  public Document getDocument() {
    return myDocument;
  }

  public int getSelectionStartLineNumber() {
    return mySelectionStartLineNumber;
  }

  public int getSelectionEndLineNumber() {
    return mySelectionEndLineNumber;
  }

  public String getSelectedAreaName() {
    return mySelectedAreaName;
  }
}