// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

public class DocumentCopyUndoTest extends UndoTestCase {
  private Editor myEditor;
  private Editor myEditorCopy1;
  private Editor myEditorCopy2;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    VirtualFile f = createChildData(myRoot, "f.java");

    Document doc = FileDocumentManager.getInstance().getDocument(f);
    Document docCopy1 = getEditorFactory().createDocument("");
    Document docCopy2 = getEditorFactory().createDocument("");

    myEditor = getEditorFactory().createEditor(doc);
    myEditorCopy1 = getEditorFactory().createEditor(docCopy1);
    myEditorCopy2 = getEditorFactory().createEditor(docCopy2);

    docCopy1.putUserData(UndoManager.ORIGINAL_DOCUMENT, doc);
    docCopy2.putUserData(UndoManager.ORIGINAL_DOCUMENT, doc);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      getEditorFactory().releaseEditor(myEditor);
      getEditorFactory().releaseEditor(myEditorCopy1);
      getEditorFactory().releaseEditor(myEditorCopy2);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myEditor = null;
      myEditorCopy1 = null;
      myEditorCopy2 = null;
      super.tearDown();
    }
  }

  private static EditorFactory getEditorFactory() {
    return EditorFactory.getInstance();
  }

  public void testEditingOriginalDocAffectsAllItsCopies() {
    assertUndoNotAvailableInAllEditors();

    typeInText(myEditor, "hello");
    assertUndoIsAvailableInAllEditors();
  }

  public void testEditingOriginalDocAffectsAllItsCopiesAfterCommandMergerWasFlushed() {
    typeInText(myEditor, "hello");
    myManager.flushCurrentCommandMerger();

    assertUndoIsAvailableInAllEditors();
  }

  public void testEditingCopyDoesNotAffectNeitherOriginalDocNorAnyItsCopies() {
    assertUndoNotAvailableInAllEditors();

    typeInText(myEditorCopy1, "hello");
    typeInText(myEditorCopy2, "hello");
    assertUndoNotAvailableInAllEditors();
  }

  public void testUndoingOriginalDocDoesNotAffectCopies() {
    typeInText(myEditor, "hello");
    typeInText(myEditorCopy1, "hello1");
    typeInText(myEditorCopy2, "hello2");

    undo(myEditor);

    assertEquals("", myEditor.getDocument().getText());
    assertEquals("hello1", myEditorCopy1.getDocument().getText());
    assertEquals("hello2", myEditorCopy2.getDocument().getText());
  }

  public void testUndoingCopyAfterEditingOriginalDocChangesOnlyOriginalDoc() {
    typeInText(myEditor, "hello");
    typeInText(myEditorCopy1, "hello1");
    typeInText(myEditorCopy2, "hello2");

    undo(myEditorCopy1);

    assertEquals("", myEditor.getDocument().getText());
    assertEquals("hello1", myEditorCopy1.getDocument().getText());
    assertEquals("hello2", myEditorCopy2.getDocument().getText());
  }

  public void testUndoNotAvailableInAllEditorsAfterUndoStackBecomesEmptyInOriginalDoc() {
    typeInText(myEditor, "hello");
    undo(myEditor);
    assertUndoNotAvailableInAllEditors();
  }

  public void testUndoNotAvailableInAllEditorsAfterUndoStackBecomesEmptyInDocCopy() {
    typeInText(myEditor, "hello");
    undo(myEditorCopy1);
    assertUndoNotAvailableInAllEditors();
  }

  private void assertUndoIsAvailableInAllEditors() {
    assertUndoIsAvailable(myEditor);
    assertUndoIsAvailable(myEditorCopy1);
    assertUndoIsAvailable(myEditorCopy2);
  }

  private void assertUndoNotAvailableInAllEditors() {
    assertUndoNotAvailable(myEditor);
    assertUndoNotAvailable(myEditorCopy1);
    assertUndoNotAvailable(myEditorCopy2);
  }
}
