package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;

/**
 * @author max
 */
public class PersistentRangeMarker extends RangeMarkerImpl {
  private int myStartLine;
  private int myStartColumn;
  private int myEndLine;
  private int myEndColumn;

  public PersistentRangeMarker(Document document, int startOffset, int endOffset) {
    super(document, startOffset, endOffset);
    storeLinesAndCols();
  }

  private void storeLinesAndCols() {
    myStartLine = myDocument.getLineNumber(getStartOffset());
    myStartColumn = getStartOffset() - myDocument.getLineStartOffset(myStartLine);
    myEndLine = myDocument.getLineNumber(getEndOffset());
    myEndColumn = getEndOffset() - myDocument.getLineStartOffset(myEndLine);
  }

  public void documentChanged(DocumentEvent e) {
    if (!isValid()) return;

    DocumentEventImpl event = (DocumentEventImpl)e;
    if (event.isWholeTextReplaced()){
      myStartLine = event.translateLineViaDiffStrict(myStartLine);
      if (myStartLine < 0 || myStartLine >= getDocument().getLineCount()){
        invalidate();
      }
      else{
        myStart = getDocument().getLineStartOffset(myStartLine) + myStartColumn;
      }

      myEndLine = event.translateLineViaDiffStrict(myEndLine);
      if (myEndLine < 0 || myEndLine >= getDocument().getLineCount()){
        invalidate();
      }
      else{
        myEnd = getDocument().getLineStartOffset(myEndLine) + myEndColumn;
      }
    }
    else{
      super.documentChanged(e);
      if (isValid()){
        storeLinesAndCols();
      }
    }
  }
}
