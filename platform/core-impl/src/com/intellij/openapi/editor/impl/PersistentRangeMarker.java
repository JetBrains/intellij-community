/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.util.diff.FilesTooBigForDiffException;

/**
 * This class is an extension to range marker that tries to restore its range even in situations when target text referenced by it
 * is replaced.
 * <p/>
 * Example: consider that the user selects all text at editor (Ctrl+A), copies it to the buffer (Ctrl+C) and performs paste (Ctrl+V).
 * All document text is replaced then but in essence it's the same, hence, we may want particular range markers to be still valid.
 *
 * @author max
 */
class PersistentRangeMarker extends RangeMarkerImpl {
  private int myStartLine;
  private int myStartColumn;
  private int myEndLine;
  private int myEndColumn;

  PersistentRangeMarker(DocumentEx document, int startOffset, int endOffset, boolean register) {
    super(document, startOffset, endOffset, register);
    storeLinesAndCols(null);
  }

  private void storeLinesAndCols(DocumentEvent e) {
    // document might have been changed already
    int startOffset = getStartOffset();
    if (startOffset <= myDocument.getTextLength()) {
      myStartLine = myDocument.getLineNumber(startOffset);
      myStartColumn = startOffset - myDocument.getLineStartOffset(myStartLine);
      if (myStartColumn < 0) {
        invalidate(e);
      }
    }
    else {
      invalidate(e);
    }
    int endOffset = getEndOffset();
    if (endOffset <= myDocument.getTextLength()) {
      myEndLine = myDocument.getLineNumber(endOffset);
      myEndColumn = endOffset - myDocument.getLineStartOffset(myEndLine);
      if (myEndColumn < 0) {
        invalidate(e);
      }
    }
    else {
      invalidate(e);
    }
  }

  private boolean translateViaDiff(final DocumentEventImpl event) {
    try {
      myStartLine = event.translateLineViaDiffStrict(myStartLine);
      DocumentEx document = getDocument();
      if (myStartLine < 0 || myStartLine >= document.getLineCount()) {
        invalidate(event);
      }
      else {
        int start = document.getLineStartOffset(myStartLine) + myStartColumn;
        if (start >= document.getTextLength()) return false;
        setIntervalStart(start);
      }

      myEndLine = event.translateLineViaDiffStrict(myEndLine);
      if (myEndLine < 0 || myEndLine >= document.getLineCount()) {
        invalidate(event);
      }
      else {
        int end = document.getLineStartOffset(myEndLine) + myEndColumn;
        if (end > document.getTextLength()) return false;
        setIntervalEnd(end);
      }
      return true;
    }
    catch (FilesTooBigForDiffException e) {
      return false;
    }
  }

  @Override
  protected void changedUpdateImpl(DocumentEvent e) {
    DocumentEventImpl event = (DocumentEventImpl)e;
    final boolean shouldTranslateViaDiff = PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, this);
    boolean wasTranslated = shouldTranslateViaDiff;
    if (shouldTranslateViaDiff) {
      wasTranslated = translateViaDiff(event);
    }
    if (!wasTranslated) {
      super.changedUpdateImpl(e);
      if (isValid()) {
        storeLinesAndCols(e);
      }
    }
    if (intervalEnd() < intervalStart() ||
        intervalEnd() > getDocument().getTextLength() ||
        myEndLine < myStartLine ||
        myStartLine == myEndLine && myEndColumn < myStartColumn ||
        getDocument().getLineCount() < myEndLine) {
      invalidate(e);
    }
  }

  @Override
  public String toString() {
    return "PersistentRangeMarker" +
           (isGreedyToLeft() ? "[" : "(") +
           (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() +
           " " + myStartLine + ":" + myStartColumn + "-" + myEndLine + ":" + myEndColumn +
           (isGreedyToRight() ? "]" : ")");
  }
}
