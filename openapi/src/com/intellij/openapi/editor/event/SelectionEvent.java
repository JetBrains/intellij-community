/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;

import java.util.EventObject;

public class SelectionEvent extends EventObject {
  private final int myOldSelectionStart;
  private final int myOldSelectionEnd;
  private final int myNewSelectionStart;
  private final int myNewSelectionEnd;

  public SelectionEvent(Editor editor,
                        int oldSelectionStart, int oldSelectionEnd,
                        int newSelectionStart, int newSelectionEnd) {
    super(editor);

    myOldSelectionStart = oldSelectionStart;
    myOldSelectionEnd = oldSelectionEnd;
    myNewSelectionStart = newSelectionStart;
    myNewSelectionEnd = newSelectionEnd;
  }

  public Editor getEditor() {
    return (Editor) getSource();
  }

  public TextRange getOldRange() {
    return new TextRange(myOldSelectionStart, myOldSelectionEnd);
  }

  public TextRange getNewRange() {
    return new TextRange(myNewSelectionStart, myNewSelectionEnd);
  }
}
