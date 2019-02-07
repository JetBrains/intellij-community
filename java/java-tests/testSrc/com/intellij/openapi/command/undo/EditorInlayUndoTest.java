// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Inlay;
import com.intellij.testFramework.EditorTestUtil;

public class EditorInlayUndoTest extends EditorUndoTestCase {
  @Override
  protected String initialDocText() {
    return "ab";
  }

  public void testUndoWithCaretBeforeInlay() {
    addInlay(1);
    moveCaret(RIGHT, false);
    backspace(getFirstEditor());
    checkEditorText("b");
    undoFirstEditor();
    checkEditorText("ab");
    checkCaretPosition(1, 1, 1);
  }

  public void testUndoWithCaretAfterInlay() {
    addInlay(1);
    moveCaret(2, RIGHT, false);
    delete(getFirstEditor());
    checkEditorText("a");
    undoFirstEditor();
    checkEditorText("ab");
    checkCaretPosition(1, 1, 2);
  }

  public void testUndoTypingBetweenAdjacentInlays() {
    addInlay(1);
    addInlay(1);
    moveCaret(2, RIGHT, false);
    typeInChar(' ');
    checkEditorText("a b");
    checkCaretPosition(2, 2, 3);
    undoFirstEditor();
    checkEditorText("ab");
    checkCaretPosition(1, 1, 2);
  }

  private Inlay addInlay(int offset) {
    return EditorTestUtil.addInlay(getFirstEditor(), offset);
  }

  private void checkCaretPosition(int offset, int logicalColumn, int visualColumn) {
    CaretModel caretModel = getFirstEditor().getCaretModel();
    assertEquals(offset, caretModel.getOffset());
    assertEquals(0, caretModel.getLogicalPosition().line);
    assertEquals(logicalColumn, caretModel.getLogicalPosition().column);
    assertEquals(0, caretModel.getVisualPosition().line);
    assertEquals(visualColumn, caretModel.getVisualPosition().column);
  }
}
