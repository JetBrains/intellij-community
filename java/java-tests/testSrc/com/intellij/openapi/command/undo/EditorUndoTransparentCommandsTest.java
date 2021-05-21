// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;


public class EditorUndoTransparentCommandsTest extends EditorUndoTestCase {
  @Override
  protected String initialDocText() {
    return "test";
  }

  public void testTransparentUndoRedo() {
    String initial = getFirstEditor().getDocument().getText();

    executeCommand(() -> typeInText("a"));
    executeTransparently(() -> typeInText("1"));
    String afterTransparent = getFirstEditor().getDocument().getText();

    undo(getFirstEditor());
    checkEditorText(initial, getFirstEditor());

    redo(getFirstEditor());
    checkEditorText(afterTransparent, getFirstEditor());
  }

  public void testTransparentUndoWithCaretMoves() {
    moveCaret(1, RIGHT, false);

    final Document doc = EditorFactory.getInstance().createDocument("Some another document");
    executeTransparently(() -> WriteCommandAction.runWriteCommandAction(null, () -> doc.deleteString(0, 5)));

    moveCaret(1, LEFT, false);
    typeInChar('c');
    undoFirstEditor();
    checkEditorState("test", 0, 0, 0);
  }

  public void testUndoTransparentDoesntMergeCommands() {
    typeInText("foo");
    checkEditorState("footest", 3, 0, 0);

    executeTransparently(() -> WriteCommandAction.runWriteCommandAction(null, () -> getFirstEditor().getDocument().insertString(0, "bar")));
    checkEditorState("barfootest", 6, 0, 0);

    executeCommand(
      () -> ApplicationManager.getApplication().runWriteAction(() -> getFirstEditor().getDocument().insertString(0, "___")),
      "Dummy");
    checkEditorState("___barfootest", 9, 0, 0);

    undoFirstEditor();
    checkEditorState("barfootest", 6, 0, 0);

    undoFirstEditor();
    checkEditorState("test", 0, 0, 0);
  }

  public void testTransparentActionsDoNotBreakCommandGroups() {
    String initial = getFirstEditor().getDocument().getText();

    typeInText("a");
    executeTransparently(() -> typeInText("b"));
    typeInText("c");
    executeTransparently(() -> {
    });
    typeInText("d");
    String after = getFirstEditor().getDocument().getText();

    undo(getFirstEditor());
    checkEditorText(initial, getFirstEditor());

    redo(getFirstEditor());
    checkEditorText(after, getFirstEditor());
  }

  public void testTransparentActionAfterUndo() {
    String initial = getFirstEditor().getDocument().getText();

    executeCommand(() -> typeInText("a"));
    String afterType1 = getFirstEditor().getDocument().getText();

    executeCommand(() -> typeInText("b"));
    executeTransparently(() -> typeInText("1"));

    undo(getFirstEditor());
    checkEditorText(afterType1, getFirstEditor());

    executeTransparently(() -> typeInText("2"));

    undo(getFirstEditor());
    checkEditorText(initial, getFirstEditor());
  }

  public void testTransparentActionBeforeRedo() {
    Editor editor = getFirstEditor();

    executeCommand(() -> typeInText("a"));
    String afterType1 = editor.getDocument().getText();

    executeCommand(() -> typeInText("b"));
    String afterType2 = editor.getDocument().getText();

    undo(editor);
    checkEditorText(afterType1, editor);

    executeTransparently(() -> typeInText("1"));

    assertUndoIsAvailable(editor);
    assertRedoIsAvailable(editor);

    redo(editor);
    checkEditorText(afterType2, editor);
    assertUndoIsAvailable(editor);
    assertRedoNotAvailable(editor);

    undo(editor);
    checkEditorText(afterType1, editor);
    assertUndoIsAvailable(editor);
    assertRedoIsAvailable(editor);

    redo(editor);
    checkEditorText(afterType2, editor);
    assertUndoIsAvailable(editor);
    assertRedoNotAvailable(editor);
  }

  public void testDoNotDropTransparentCommandsFromTheMiddleOfTheStacksUndo2() {
    String initial = getFirstEditor().getDocument().getText();

    executeCommand(() -> typeInText("a"));
    executeTransparently(() -> typeInText("1"));
    String afterTransp1 = getFirstEditor().getDocument().getText();

    executeCommand(() -> typeInText("b"));
    executeTransparently(() -> typeInText("2"));
    String afterTransp2 = getFirstEditor().getDocument().getText();

    undo(getFirstEditor());
    checkEditorText(afterTransp1, getFirstEditor());

    undo(getFirstEditor());
    checkEditorText(initial, getFirstEditor());

    redo(getFirstEditor());
    checkEditorText(afterTransp1, getFirstEditor());

    redo(getFirstEditor());
    checkEditorText(afterTransp2, getFirstEditor());
  }

  public void testTransparentCommandsInDifferentDocumentsDoNotCorruptUndoStack() {
    getFirstEditor().getCaretModel().moveToOffset(getFirstEditor().getDocument().getTextLength());
    typeInChar(' ');
    moveCaret(LEFT, false);
    executeTransparentlyInWriteAction(() -> getFirstEditor().getDocument().insertString(0, " "));
    executeTransparentlyInWriteAction(() -> getSecondEditor().getDocument().insertString(0, " "));
    delete(getFirstEditor());

    checkEditorText(" test");
    while (isUndoAvailable(getFirstEditor())) undo(getFirstEditor());
    checkEditorText("test");
    while (isRedoAvailable(getFirstEditor())) redo(getFirstEditor());
    checkEditorText(" test");
  }

  public void testTransparentCommandInHiddenEditorDoesNotClearSavedState() {
    for (int i = 0; i < 6; i++) {
      executeEditorAction(getFirstEditor(), IdeActions.ACTION_EDITOR_ENTER);
    }
    typeInChar('a');

    CurrentEditorProvider savedProvider = myManager.getEditorProvider();
    try {
      myManager.setEditorProvider(() -> null);
      executeTransparentlyInWriteAction(() -> getFirstEditor().getDocument().insertString(0, " "));
    }
    finally {
      myManager.setEditorProvider(savedProvider);
    }
    executeEditorAction(getFirstEditor(), IdeActions.ACTION_EDITOR_TEXT_START);

    undo(getFirstEditor());
    checkEditorText("\n\n\n\n\n\natest");
    undo(getFirstEditor());
    checkEditorText("\n\n\n\n\n\ntest");
  }

  public void testNormalActionAfterTemporaryActionDoesNotBreakUndoStack() {
    getFirstEditor().getCaretModel().moveToOffset(getFirstEditor().getDocument().getTextLength());
    typeInChar('a');
    moveCaret(LEFT, false);
    typeInChar('b');
    undo(getFirstEditor());
    checkEditorText("testa");
    executeTransparentlyInWriteAction(() -> getFirstEditor().getDocument().insertString(0, " "));
    executeTransparentlyInWriteAction(() -> getSecondEditor().getDocument().insertString(0, " "));
    typeInChar('c');
    while (isUndoAvailable(getFirstEditor())) undo(getFirstEditor());
    checkEditorText("test");
    while (isRedoAvailable(getFirstEditor())) redo(getFirstEditor());
    checkEditorText(" testca");
  }

  public void testMultiDocumentChangeAfterTransparentActionDoesNotBreakUndoStack() {
    executeTransparentlyInWriteAction(() -> getFirstEditor().getDocument().insertString(4, " "));
    executeCommand(() -> {
      typeInChar('a');
      typeInChar(getSecondEditor(), 'A');
    });

    while (isUndoAvailable(getFirstEditor())) undo(getFirstEditor());
    checkEditorText("test");
    while (isRedoAvailable(getFirstEditor())) redo(getFirstEditor());
    checkEditorText("atest ");
  }

  public void testNoConfirmationForTransparentAction() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT); // throw an exception if there's a need to request confirmation from user

    executeTransparentlyInWriteAction(() -> CommandProcessor.getInstance()
      .executeCommand(myProject, () -> typeInChar(' '), "", null, UndoConfirmationPolicy.REQUEST_CONFIRMATION));

    undo(getFirstEditor());
    checkEditorText("test");
  }

  private static <T extends Exception> void executeTransparentlyInWriteAction(@NotNull ThrowableRunnable<T> action) {
    executeTransparently(() -> WriteAction.run(action));
  }
}
