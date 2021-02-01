// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.history.integration.LocalHistoryImpl;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class UndoTestCase extends JavaCodeInsightTestCase {
  private CurrentEditorProvider myOldEditorProvider;

  protected UndoManagerImpl myManager;
  protected VirtualFile myRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myManager = (UndoManagerImpl)UndoManager.getInstance(myProject);
    myOldEditorProvider = myManager.getEditorProvider();

    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        setUpInWriteAction();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myManager.setEditorProvider(myOldEditorProvider);
      myManager = null;
      myOldEditorProvider = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected void initApplication() throws Exception {
    super.initApplication();
    LocalHistoryImpl.getInstanceImpl().cleanupForNextTest();
  }

  protected void setUpInWriteAction() throws Exception {
    myRoot = createTestProjectStructure();
  }

  void typeInChar(Editor e, char c) {
    EditorActionManager.getInstance();
    TypedAction.getInstance().actionPerformed(e, c, createDataContextFor(e));
  }

  protected void typeInText(Editor editor, String text) {
    char[] chars = text.toCharArray();
    for (char aChar : chars) {
      typeInChar(editor, aChar);
    }
  }

  protected static void moveCaret(final Editor e, final String dir, final boolean selection) {
    executeEditorAction(e, "Editor" + dir + (selection ? "WithSelection" : ""));
  }

  protected static void enter(final Editor e) {
    executeEditorAction(e, IdeActions.ACTION_EDITOR_ENTER);
  }

  @Override
  protected void backspace(@NotNull final Editor e) {
    executeEditorAction(e, IdeActions.ACTION_EDITOR_BACKSPACE);
  }

  @Override
  protected void delete(@NotNull final Editor e) {
    executeEditorAction(e, IdeActions.ACTION_EDITOR_DELETE);
  }

  static void executeEditorAction(@NotNull Editor editor, @NotNull String actionId) {
    EditorTestUtil.executeAction(editor, actionId);
  }

  VirtualFile createFileInCommand(final String name) {
    try {
      return WriteCommandAction
        .runWriteCommandAction(getProject(), (ThrowableComputable<VirtualFile, IOException>)() -> myRoot.createChildData(this, name));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void addContentRoot() {
    PsiTestUtil.addContentRoot(getModule(), getTempDir().createVirtualDir());
  }

  protected void executeCommand(@NotNull Runnable command, String name) {
    CommandProcessor.getInstance().executeCommand(myProject, command, name, null);
  }

  private DataContext createDataContextFor(final Editor editor) {
    return dataId -> {
      if (CommonDataKeys.EDITOR.is(dataId)) return editor;
      if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
      return null;
    };
  }

  boolean isUndoAvailable(Editor e) {
    return myManager.isUndoAvailable(getFileEditor(e));
  }

  protected void undo(Editor e) {
    FileEditor fe = getFileEditor(e);
    assertTrue("undo is not available", myManager.isUndoAvailable(fe));
    myManager.undo(fe);
  }

  boolean isRedoAvailable(Editor e) {
    return myManager.isRedoAvailable(getFileEditor(e));
  }

  void redo(Editor e) {
    FileEditor fe = getFileEditor(e);
    assertTrue("redo is not available", myManager.isRedoAvailable(fe));
    myManager.redo(fe);
  }

  void globalUndo() {
    undo(null);
  }

  void globalRedo() {
    redo(null);
  }

  protected Editor getEditor(VirtualFile file) {
    return FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
  }

  static void assertStartsWith(String prefix, String text) {
    assertTrue(text, text.startsWith(prefix));
  }

  void assertGlobalUndoIsAvailable() {
    assertUndoIsAvailable(null);
  }

  void assertGlobalUndoNotAvailable() {
    assertUndoNotAvailable(null);
  }

  void assertGlobalRedoIsAvailable() {
    assertRedoIsAvailable(null);
  }

  void assertGlobalRedoNotAvailable() {
    assertRedoNotAvailable(null);
  }

  void assertRedoNotAvailable(Editor e) {
    assertFalse(myManager.isRedoAvailable(getFileEditor(e)));
  }

  void assertUndoIsAvailable(Editor e) {
    assertTrue(myManager.isUndoAvailable(getFileEditor(e)));
  }

  void assertUndoNotAvailable(Editor e) {
    assertFalse(myManager.isUndoAvailable(getFileEditor(e)));
  }

  void assertRedoIsAvailable(Editor e) {
    assertTrue(myManager.isRedoAvailable(getFileEditor(e)));
  }

  protected static FileEditor getFileEditor(Editor e) {
    return e == null ? null : TextEditorProvider.getInstance().getTextEditor(e);
  }

  protected void executeCommand(Command c) {
    executeCommand("", c);
  }

  protected void executeCommand(String name, Command command) {
    executeCommand(name, null, command);
  }

  protected void executeCommand(final String name, final Object groupId, final Command command) {
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      try {
        command.run();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }, name, groupId);
  }

  static void executeTransparently(final Command r) {
    DocumentUtil.writeInRunUndoTransparentAction(() -> {
      try {
        r.run();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @FunctionalInterface
  protected interface Command {
    void run() throws Exception;
  }
}
