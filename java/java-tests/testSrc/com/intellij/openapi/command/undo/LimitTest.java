// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.command.impl.UndoManagerImpl;

/**
 * author: lesya
 */
public class LimitTest extends EditorUndoTestCase {
  private static final int MORE_THEN_LOCAL_LIMIT = UndoManagerImpl.getDocumentUndoLimit() + 1;

  public void testLocalUndoLimit() {

    for (int i = 0; i < MORE_THEN_LOCAL_LIMIT; i++) {
      doAnyLocalChanges();
    }

    for (int i = 0; i < UndoManagerImpl.getDocumentUndoLimit(); i++) {
      undoFirstEditor();
    }

    assertUndoInFirstEditorNotAvailable();
  }

  public void testGlobalUndoLimit() {

    for (int i = 0; i < MORE_THEN_LOCAL_LIMIT; i++) {
      doAnyGlobalChanges();
    }

    for (int i = 0; i < UndoManagerImpl.getGlobalUndoLimit(); i++) {
      undoFirstEditor();
    }

    assertGlobalUndoNotAvailable();
  }

  private void doAnyGlobalChanges() {
    typeInTextToAllDocuments("1");
  }

  private void doAnyLocalChanges() {
    typeInText("1");
    enter();
  }
}
