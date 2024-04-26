// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import org.jetbrains.annotations.Nullable;

public class StartFinishUndoTest extends EditorUndoTestCase {
  public void testCompoundUndo() {
    final StartMarkAction[] compound = new StartMarkAction[1];
    start(compound);

    assertNotNull(compound[0]);
    try {
      typeInText("text");
      backspace(getFirstEditor());
      typeInText("TT");
    }
    finally {
      finish(compound[0]);
    }

    checkEditorText("texTT");
    undoFirstEditor();
    assertUndoInFirstEditorNotAvailable();
    checkEditorText("");
    redoFirstEditor();
    assertRedoInFirstEditorNotAvailable();
    checkEditorText("texTT");
  }

  public void testLeaveBeforeStartUnchangedAfterUndo() {
    typeInText("initial ");
    final StartMarkAction[] compound = new StartMarkAction[1];
    start(compound);

    assertNotNull(compound);
    try {
      typeInText("text");
      backspace(getFirstEditor());
      typeInText("TT");
    }
    finally {
      finish(compound[0]);
    }

    checkEditorText("initial texTT");
    undoFirstEditor();
    checkEditorText("initial ");
    assertUndoInFirstEditorIsAvailable();
    redoFirstEditor();
    assertRedoInFirstEditorNotAvailable();
    checkEditorText("initial texTT");
  }

  public void testGlobalCommandDuringCompoundUndo() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT);
    final StartMarkAction[] compound = new StartMarkAction[1];
    start(compound);

    assertNotNull(compound);
    try {
      typeInText("text");
      backspace(getFirstEditor());
      CommandProcessor.getInstance().executeCommand(getProject(), () -> {
        typeInText("TT");
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(getProject());
      }, "global command", null);
    }
    finally {
      finish(compound[0]);
    }

    checkEditorText("texTT");
    try {
      undoFirstEditor();
      fail("Confirmation should be requested");
    }
    catch (Exception e) {
      assertEquals("Undo Typing?", e.getMessage());
      assertUndoInFirstEditorIsAvailable();
    }
  }

  public void testGlobalCommandInBothEditors() {
    final StartMarkAction[] compound = new StartMarkAction[1];
    start(compound);
    try {
      myManager.setOverriddenEditorProvider(new CurrentEditorProvider() {
        @Override
        public FileEditor getCurrentEditor(@Nullable Project project) {
          return getFileEditor(getSecondEditor());
        }
      });
      typeInText(getSecondEditor(), "initial ");
    }
    finally {
      myManager.setOverriddenEditorProvider(null);
    }

    assertNotNull(compound);
    try {
      typeInText("text");
      backspace(getFirstEditor());
      CommandProcessor.getInstance().executeCommand(getProject(), () -> {
        typeInTextToAllDocuments("TT");
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(getProject());
      }, "global command", null);
    }
    finally {
      finish(compound[0]);
    }


    checkEditorText("texTT");
    checkEditorText("initial TT", getSecondEditor());
    undo(getFirstEditor());
    checkEditorText("initial ", getSecondEditor());
    try {
      myManager.setOverriddenEditorProvider(new CurrentEditorProvider() {
        @Override
        public FileEditor getCurrentEditor(@Nullable Project project) {
          return getFileEditor(getSecondEditor());
        }
      });
      undo(getSecondEditor());
    }
    finally {
      myManager.setOverriddenEditorProvider(null);
    }
    checkEditorText("", getSecondEditor());
    assertUndoNotAvailable(getSecondEditor());
  }

  public void testUserConfirmation() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT);
    final StartMarkAction[] compound = new StartMarkAction[1];
    start(compound);

    assertNotNull(compound);
    try {
      typeInText("text");
      backspace(getFirstEditor());
      CommandProcessor.getInstance().executeCommand(getProject(), () -> {
        typeInText("TT");
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(getProject());
      }, "global command", null, UndoConfirmationPolicy.REQUEST_CONFIRMATION);
    }
    finally {
      finish(compound[0]);
    }

    checkEditorText("texTT");
    try {
      undoFirstEditor();
      fail("User confirmation should be asked");
    }
    catch (Exception e) {
      assertEquals("Undo Typing?", e.getMessage());
    }
  }

  public void testMultipleStartFinishSeq() {
    typeInText("initial");
    final StartMarkAction[] compound = new StartMarkAction[1];
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      try {
        final StartMarkAction markAction = StartMarkAction.start(getFirstEditor(), getProject(), "compound");
        typeInText("to undo1");
        finish(markAction);
        compound[0] = StartMarkAction.start(getFirstEditor(), getProject(), "compound");
      }
      catch (StartMarkAction.AlreadyStartedException e) {
        e.printStackTrace();
      }
    }, "start", null, getFirstEditor().getDocument());
    typeInText("to undo2");
    finish(compound[0]);
    undoFirstEditor();
    checkEditorText("initial");
  }

  private void start(final StartMarkAction[] compound) {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      try {
        compound[0] = StartMarkAction.start(getFirstEditor(), getProject(), "compound");
      }
      catch (StartMarkAction.AlreadyStartedException e) {
        e.printStackTrace();
      }
    }, "start", null, getFirstEditor().getDocument());
  }

  private void finish(final StartMarkAction action) {
    FinishMarkAction.finish(getProject(), getFirstEditor(), action);
  }
}
