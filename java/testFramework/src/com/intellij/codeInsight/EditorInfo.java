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
package com.intellij.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public class EditorInfo {
  @NonNls public static final String CARET_MARKER = "<caret>";
  @NonNls public static final String SELECTION_START_MARKER = "<selection>";
  @NonNls public static final String SELECTION_END_MARKER = "</selection>";

  String newFileText = null;
  public RangeMarker caretMarker = null;
  RangeMarker selStartMarker = null;
  RangeMarker selEndMarker = null;

  public EditorInfo(final String fileText) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        updateCaretAndSelection(EditorFactory.getInstance().createDocument(fileText));
      }
    });
  }

  private boolean updateCaretAndSelection(final Document document) {
    newFileText = document.getText();

    int caretIndex = newFileText.indexOf(CARET_MARKER);
    int selStartIndex = newFileText.indexOf(SELECTION_START_MARKER);
    int selEndIndex = newFileText.indexOf(SELECTION_END_MARKER);

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
    return caretMarker != null || selStartMarker != null || selEndMarker != null;
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
