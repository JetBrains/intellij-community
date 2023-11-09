// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class EditorUndoTestCase extends UndoTestCase {
  public static final String UP = "Up";
  public static final String DOWN = "Down";
  public static final String LEFT = "Left";
  public static final String RIGHT = "Right";

  private TestDialog myOldTestDialog;

  protected Editor[] myEditors = new Editor[2];
  protected Editor myView;
  protected boolean mySecondEditorSelected;

  @Override
  protected void setUp() throws Exception {
    myOldTestDialog = TestDialogManager.setTestDialog(TestDialog.OK);

    super.setUp();

    myManager.setEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor(@Nullable Project project) {
        return getFileEditor(mySecondEditorSelected ? getSecondEditor() : getFirstEditor());
      }
    });

    WriteAction.runAndWait(() -> initEditors());


    getFirstEditor().getCaretModel().moveToOffset(0);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EditorFactory editorFactory = EditorFactory.getInstance();

      for (Editor editor : myEditors) {
        if (editor != null) editorFactory.releaseEditor(editor);
      }
      if (myView != null) editorFactory.releaseEditor(myView);

      TestDialogManager.setTestDialog(myOldTestDialog);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myView = null;
      myEditors = null;
      myOldTestDialog = null;
      super.tearDown();
    }
  }

  private void initEditors() throws IOException {
    EditorFactory f = EditorFactory.getInstance();

    VirtualFile file1 = myRoot.createChildData(this, "_editorFile1.txt");
    VirtualFile file2 = myRoot.createChildData(this, "_editorFile2.txt");
    setFileText(file1, initialDocText());
    setFileText(file2, initialDocText());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    myEditors[0] = f.createEditor(FileDocumentManager.getInstance().getDocument(file1), myProject);
    myEditors[1] = f.createEditor(FileDocumentManager.getInstance().getDocument(file2), myProject);

    myView = f.createViewer(f.createDocument(initialDocText()));
  }

  protected String initialDocText() {
    return "";
  }

  protected void typeInText(String text) {
    typeInText(getFirstEditor(), text);
  }

  protected void typeInChar(char c) {
    typeInChar(getFirstEditor(), c);
  }

  protected void moveCaret(int n, String dir, boolean selection) {
    for (int i = 0; i < n; i++) moveCaret(dir, selection);
  }

  protected void moveCaret(String dir, final boolean selection) {
    moveCaret(getFirstEditor(), dir, selection);
  }

  protected void moveCaretOutsideCommand(int offset) {
    getFirstEditor().getCaretModel().moveToOffset(offset);
  }

  protected void enter() {
    enter(getFirstEditor());
  }

  protected void undoFirstEditor() {
    assertUndoInFirstEditorIsAvailable();
    undo(getFirstEditor());
  }

  protected void redoFirstEditor() {
    assertRedoInFirstEditorIsAvailable();
    redo(getFirstEditor());
  }

  protected void assertUndoInFirstEditorIsAvailable() {
    assertUndoIsAvailable(getFirstEditor());
  }

  protected void assertUndoInFirstEditorNotAvailable() {
    assertUndoNotAvailable(getFirstEditor());
  }

  protected void assertRedoInFirstEditorIsAvailable() {
    assertRedoIsAvailable(getFirstEditor());
  }

  protected void assertRedoInFirstEditorNotAvailable() {
    assertRedoNotAvailable(getFirstEditor());
  }

  protected Editor getFirstEditor() {
    return myEditors[0];
  }

  protected Editor getSecondEditor() {
    return myEditors[1];
  }

  protected void selectSecondEditor() {
    mySecondEditorSelected = true;
  }

  protected void typeInTextToAllDocuments(final String s) {
    Runnable command = () -> {
      for (Editor e : myEditors) typeInText(e, s);
    };
    executeCommand(command, "Complex typing");
  }

  protected void checkEditorState(String text, int caretOffset, int selectionStart, int selectionEnd) {
    checkEditorState(getFirstEditor(), text, caretOffset, selectionStart, selectionEnd);
  }

  protected void checkEditorText(String text, Editor e) {
    assertEquals(text, e.getDocument().getText());
  }

  protected void checkEditorText(String text) {
    assertEquals(text, getFirstEditor().getDocument().getText());
  }

  protected static void checkEditorState(Editor editor, String text, int caretOffset, int selectionStart, int selectionEnd) {
    assertEquals("doc text", text, editor.getDocument().getText());
    assertEquals("caret", caretOffset, editor.getCaretModel().getOffset());
    SelectionModel selectionModel = editor.getSelectionModel();
    assertEquals("selection precense", selectionStart != selectionEnd, selectionModel.hasSelection());
    if (selectionModel.hasSelection()) {
      assertEquals("selection start", selectionStart, selectionModel.getSelectionStart());
      assertEquals("selection end", selectionEnd, selectionModel.getSelectionEnd());
    }
  }
}
