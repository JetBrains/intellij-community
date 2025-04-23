// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ComplexUndoTest extends EditorUndoTestCase {
  private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

  public void testEditAllDocument() {
    typeInTextToAllDocuments("text1");
    typeInTextToAllDocuments("text2");
    undoFirstEditor();
    checkEditorsText("text1");
    redoFirstEditor();
    checkEditorsText("text1text2");
  }

  public void testChangeAnyDocumentAfterComplexAction() {
    typeInTextToAllDocuments("text");

    typeInText(getSecondEditor(), "ttt");

    try {
      undoFirstEditor();
      fail("Exception expected");
    }
    catch (Exception e) {
      assertStartsWith("The following files affected by this action have been already changed:", e.getMessage());
    }
  }

  public void testDoesNotResetGlobalRedoOnDocumentChanges() {
    createFileInCommand("f.java");

    globalUndo();
    assertGlobalRedoIsAvailable();

    typeInText("hello");

    assertGlobalRedoIsAvailable();
  }

  public void testDoesNotResetDocumentRedoOnGlobalChanges() {
    typeInText("hello");

    undoFirstEditor();
    assertRedoInFirstEditorIsAvailable();

    createFileInCommand("f.java");

    assertRedoInFirstEditorIsAvailable();
  }

  public void testResetGlobalRedoOnComplexDocumentChanges() {
    createFileInCommand("f.java");

    globalUndo();
    assertGlobalRedoIsAvailable();

    typeInTextToAllDocuments("hello");

    assertGlobalRedoNotAvailable();
  }

  public void testDoesNotLoseCharset() {
    char utf8character = '\u00e9';
    EditorTestUtil.saveEncodingsIn(myProject, null, WINDOWS_1251, () -> PlatformTestUtil.withEncoding(WINDOWS_1251.name(), () -> {
      assertEquals(CharsetToolkit.UTF8, EncodingManager.getInstance().getDefaultCharsetName());
      VirtualFile virtualFile = createFileInCommand("f.java");
      VirtualFile virtualFile2 = createFileInCommand("g.java");
      assertEquals(WINDOWS_1251, virtualFile.getCharset());
      assertEquals(WINDOWS_1251, virtualFile2.getCharset());
      EncodingProjectManager.getInstance(myProject).setEncoding(virtualFile, StandardCharsets.UTF_8);
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(StandardCharsets.UTF_8, virtualFile.getCharset());
      assertEquals(WINDOWS_1251, virtualFile2.getCharset());
      Editor editor = getEditor(virtualFile);
      Editor editor2 = getEditor(virtualFile2);
      String string = StringUtil.repeat(String.valueOf(utf8character), 1024);
      typeInText(editor, string);
      typeInText(editor2, string);
      WriteCommandAction.runWriteCommandAction(getProject(), () -> editor.getDocument().deleteString(0, editor.getDocument().getTextLength()));
      undo(editor);
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(utf8character, (int)editor.getDocument().getText().charAt(0));
      WriteCommandAction.runWriteCommandAction(getProject(), () -> editor2.getDocument().deleteString(0, editor2.getDocument().getTextLength()));
      undo(editor2);
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(utf8character, (int)editor2.getDocument().getText().charAt(0));
    }));
  }

  public void testUndoDoesntRestorePositionInWrongEditor() {
    typeInText(getFirstEditor(), "a\nb\nc\nd\ne\nf");
    typeInText(getSecondEditor(), "a\nb\nc\nd\ne\nf");
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      getFirstEditor().getDocument().insertString(0, " ");
      getSecondEditor().getDocument().insertString(0, " ");
    });
    selectSecondEditor();
    moveCaret(getSecondEditor(), UP, false);
    undo(getSecondEditor());
    checkEditorText("a\nb\nc\nd\ne\nf", getSecondEditor());
    assertEquals(9, getSecondEditor().getCaretModel().getOffset());
  }

  private void checkEditorsText(String text) {
    for (Editor e : myEditors) {
      checkEditorText(text, e);
    }
  }
}