package com.intellij.vcsUtil;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;

public class VcsSelection {
  private final Document myDocument;
  private final int mySelectionStartLineNumber;
  private final int mySelectionEndLineNumber;

  public VcsSelection(Document document, SelectionModel selectionModel) {
    this(document, new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()));
  }

  public VcsSelection(Document document, TextRange textRange) {
    myDocument = document;
    int startOffset = textRange.getStartOffset();
    mySelectionStartLineNumber = document.getLineNumber(startOffset);
    int endOffset = textRange.getEndOffset();
    mySelectionEndLineNumber = endOffset >= document.getTextLength() ? document.getLineCount() : document.getLineNumber(endOffset);
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
}