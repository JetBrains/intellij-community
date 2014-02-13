/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class EditorInfo {
  String newFileText = null;
  public EditorTestUtil.CaretsState caretState;

  public EditorInfo(final String fileText) {
    new WriteCommandAction(null){
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        updateCaretAndSelection(EditorFactory.getInstance().createDocument(fileText));
      }
    }.execute();
  }

  private void updateCaretAndSelection(final Document document) {
    caretState = EditorTestUtil.extractCaretAndSelectionMarkers(document, false);
    newFileText = document.getText();
  }

  public String getNewFileText() {
    return newFileText;
  }

  public void applyToEditor(Editor editor) {
    if (editor.getCaretModel().supportsMultipleCarets()) {
      List<LogicalPosition> caretPositions = new ArrayList<LogicalPosition>();
      List<Segment> selections = new ArrayList<Segment>();
      for (EditorTestUtil.Caret caret : caretState.carets) {
        LogicalPosition pos = null;
        if (caret.offset != null) {
          int caretLine = StringUtil.offsetToLineNumber(newFileText, caret.offset);
          int caretCol = caret.offset - StringUtil.lineColToOffset(newFileText, caretLine, 0);
          pos = new LogicalPosition(caretLine, caretCol);
        }
        caretPositions.add(pos);
        selections.add(caret.selection == null ? null : caret.selection);
      }
      editor.getCaretModel().setCarets(caretPositions, selections);
    }
    else {
      assert caretState.carets.size() == 1 : "Multiple carets are not supported by the model";
      EditorTestUtil.Caret caret = caretState.carets.get(0);
      if (caret.offset != null) {
        int caretLine = StringUtil.offsetToLineNumber(newFileText, caret.offset);
        int caretCol = caret.offset - StringUtil.lineColToOffset(newFileText, caretLine, 0);
        LogicalPosition pos = new LogicalPosition(caretLine, caretCol);
        editor.getCaretModel().moveToLogicalPosition(pos);
      }
      if (caret.selection != null) {
        editor.getSelectionModel().setSelection(caret.selection.getStartOffset(), caret.selection.getEndOffset());
      }
    }
  }
}
