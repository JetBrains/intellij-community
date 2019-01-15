// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.mock.Mock;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.FutureTask;

public class GlobalUndoTest extends UndoTestCase implements TestDialog {
  private TestDialog myOldTestDialogValue;

  private PsiClass myClass;
  private VirtualFile myDir;
  private static final String myDirName = "dir1";
  private VirtualFile myDir1;
  private VirtualFile myDir2;
  private VirtualFile myDirToMove;
  private VirtualFile myDirToRename;
  private static final String DIR_TO_RENAME_NAME = "dirToRename.txt";
  private static final String NEW_NAME = "NewName";
  private boolean myConfirmationWasRequested = false;
  private static final String DIR_NAME = "dir.txt";
  private PsiJavaFile myContainingFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldTestDialogValue = Messages.setTestDialog(this);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Messages.setTestDialog(myOldTestDialogValue);
      myContainingFile = null;
      myClass = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  public int show(String message) {
    myConfirmationWasRequested = true;
    return 0;
  }

  public void testCreateClassIsUndoableAction() {
    createClass("Class");
    globalUndo();
  }

  public void testUndoDeleteClass() {
    String className = "TestClass";
    createClass(className);

    String contentBefore = getDocumentText(findFile(className, myRoot));
    deleteClass();

    for (int i = 0; i < 2; i++) {
      globalUndo();
      assertFileExists(className, contentBefore);

      globalUndo();
      assertFileDoesNotExist(className, myRoot);

      globalRedo();
      assertFileExists(className, contentBefore);

      globalRedo();
      assertFileDoesNotExist(className, myRoot);
    }
  }

  public void testUndoCreateClassTwice() {
    createClass("TestClass1");
    createClass("TestClass2");

    Editor editor = openEditor("TestClass2.java");

    undo(editor);

    globalUndo();

    StoreUtil.saveDocumentsAndProjectsAndApp(false);

    checkAllFilesDeleted();
  }

  public void testUndoRenameClass() {
    String firstClassName = "Class1";
    String secondClassName = "Class223467234678234678236478263478";

    createClass(firstClassName);
    renameClassTo(secondClassName);

    assertFileExists(myRoot, secondClassName);
    assertFileDoesNotExist(firstClassName, myRoot);

    for (int i = 0; i < 2; i++) {
      globalUndo();
      assertFileExists(myRoot, firstClassName);
      assertFileDoesNotExist(secondClassName, myRoot);

      globalRedo();
      assertFileExists(myRoot, secondClassName);
      assertFileDoesNotExist(firstClassName, myRoot);
    }
  }

  public void testUndoRenameWithCaseChangeClass() {
    final VirtualFile f = createChildData(myRoot, "Foo.txt");

    executeCommand(() -> rename(f, "FOO.txt"));
    assertEquals("FOO.txt", f.getName());

    for (int i = 0; i < 2; i++) {
      globalUndo();
      assertEquals("Foo.txt", f.getName());

      globalRedo();
      assertEquals("FOO.txt", f.getName());
    }
  }

  private void renameClassTo(final String newClassName) {
    executeCommand((Command)() -> new RenameProcessor(myProject, myClass, newClassName, true, true).run(), "Rename Class");
  }

  public void testUndoAfterEmptyReformat() {
    createClass("foo");
    final PsiFile file = myContainingFile;
    final Editor editor = openEditor("foo.java");
    reformatFile(file);
    undo(editor);
    assertFileDoesNotExist("foo", myRoot);
  }

  private void reformatFile(final PsiFile file) throws IncorrectOperationException {
    final Runnable r = new ReformatCodeProcessor(myProject, file, null, false) {
      @Override
      @NotNull
      public FutureTask<Boolean> preprocessFile(@NotNull final PsiFile file, boolean processChangedTextOnly)
        throws IncorrectOperationException {
        return super.preprocessFile(file, false);
      }
    }.preprocessFile(file, false);

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(r), "Reformat", null,
                                                  UndoConfirmationPolicy.REQUEST_CONFIRMATION);
  }

  public void testUndoMoveFile() {
    VirtualFile dir1 = createChildDirectory(myRoot, myDirName);
    VirtualFile dir2 = createChildDirectory(myRoot, "dir2");

    String className = "Class1";
    createClass(className, dir1);
    assertFileExists(dir1, className);
    assertFileDoesNotExist(className, dir2);

    moveClassTo(dir2);
    assertFileExists(dir2, className);
    assertFileDoesNotExist(className, dir1);

    globalUndo();
    assertFileExists(dir1, className);
    assertFileDoesNotExist(className, dir2);

    globalRedo();
    assertFileExists(dir2, className);
    assertFileDoesNotExist(className, dir1);
  }

  public void testRedoCreateAndMove() {
    final VirtualFile dir = createChildDirectory(myRoot, "dir");

    final VirtualFile[] f = new VirtualFile[1];
    executeCommand(() -> {
      f[0] = createChildData(myRoot, "foo.txt");
      setDocumentText(f[0], "foo");
    });
    executeCommand(() -> {
      move(f[0], dir);
      setDocumentText(f[0], "foobar");
    });

    globalUndo();
    globalUndo();

    assertNull(myRoot.findChild("foo.txt"));

    globalRedo();
    f[0] = myRoot.findChild("foo.txt");
    assertNotNull(f[0]);
    assertEquals("foo", getDocumentText(f[0]));

    globalRedo();
    assertEquals(dir, f[0].getParent());
    assertEquals("foobar", getDocumentText(f[0]));
  }

  public void testRestoringUndoStackForDocumentWhenItIsRecreatedWithSameName() {
    final VirtualFile[] f = new VirtualFile[1];
    executeCommand(() -> {
      f[0] = createChildData(myRoot, "foo.txt");
      setDocumentText(f[0], "foo");
    });

    executeCommand(() -> setDocumentText(f[0], "foofoo"));

    undo(getEditor(f[0]));
    undo(getEditor(f[0]));
    assertNull(myRoot.findChild("foo.txt"));

    globalRedo();
    f[0] = myRoot.findChild("foo.txt");
    assertNotNull(f[0]);
    assertEquals("foo", getDocumentText(f[0]));

    redo(getEditor(f[0]));

    assertEquals("foofoo", getDocumentText(f[0]));
  }

  @NotNull
  protected static VirtualFile createChildData(@NotNull final VirtualFile dir, @NotNull @NonNls final String name) {
    try {
      return WriteAction.computeAndWait(() ->
                                          // requestor must be notnull
                                          dir.createChildData(dir, name));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void delete(@NotNull final VirtualFile file) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        // requestor must be notnull
        file.delete(file);
      }
      catch (IOException e) {
        throw new RuntimeException();
      }
    });
  }

  public void testDoNotConfuseRecreatedFilesWithDeleted() {
    final VirtualFile[] f = new VirtualFile[1];
    executeCommand(() -> {
      f[0] = createChildData(myRoot, "foo.txt");
      setDocumentText(f[0], "foo");
    });

    executeCommand(() -> setDocumentText(f[0], "foofoo"));

    executeCommand(() -> delete(f[0]));

    executeCommand(() -> f[0] = createChildData(myRoot, "foo.txt"));
    assertGlobalRedoNotAvailable();
    assertRedoNotAvailable(getEditor(f[0]));

    undo(getEditor(f[0])); // should undo creation, not editing of previous document
    assertNull(myRoot.findChild("foo.txt"));

    globalUndo();
    f[0] = myRoot.findChild("foo.txt");
    assertNotNull(f[0]);
    assertEquals("foofoo", getDocumentText(f[0]));

    undo(getEditor(f[0]));
    assertEquals("foo", getDocumentText(f[0]));
  }

  public void testDeletionOfNoneJavaFiles() {
    VirtualFile f = createChildData(myRoot, "f.xxx");

    deleteInCommand(f);
    assertGlobalUndoIsAvailable();

    globalUndo();
    assertNotNull(myRoot.findChild("f.xxx"));
  }

  public void testUndoDeleteDir() {
    createDirectory(myDirName);
    checkDirExists();

    deleteDirectory();
    checkDirDoesNotExist();

    globalUndo();
    checkDirExists();

    globalRedo();
    checkDirDoesNotExist();
  }

  @NotNull
  protected static VirtualFile createChildDirectory(@NotNull final VirtualFile dir, @NotNull @NonNls final String name) {
    try {
      return WriteAction.computeAndWait(() ->
                                          // requestor must be notnull
                                          dir.createChildDirectory(dir, name));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testUndoRenameDirectoryWithFile() {
    final VirtualFile[] dir = new VirtualFile[1];
    final VirtualFile[] file = new VirtualFile[1];
    executeCommand(() -> {
      dir[0] = createChildDirectory(myRoot, "dir");
      file[0] = createChildData(dir[0], "file.txt");
    });

    setDocumentText(file[0], "document");

    executeCommand(() -> {
      rename(dir[0], "dir2");
      setDocumentText(file[0], "document2");
    });

    for (int i = 0; i < 2; i++) {
      globalUndo();
      assertNull(myRoot.findChild("dir2"));
      dir[0] = myRoot.findChild("dir");
      assertNotNull(dir);
      file[0] = dir[0].findChild("file.txt");
      assertNotNull(file[0]);
      assertEquals("document", getDocumentText(file[0]));

      globalRedo();
      assertNull(myRoot.findChild("dir"));
      dir[0] = myRoot.findChild("dir2");
      assertNotNull(dir);
      file[0] = dir[0].findChild("file.txt");
      assertNotNull(file[0]);
      assertEquals("document2", getDocumentText(file[0]));
    }
  }

  public void testUndoDeleteDirectoryWithFile() {
    final VirtualFile[] dir = new VirtualFile[1];
    final VirtualFile[] file = new VirtualFile[1];
    executeCommand(() -> {
      dir[0] = createChildDirectory(myRoot, "dir");
      file[0] = createChildData(dir[0], "file.txt");
    });

    setDocumentText(file[0], "document");

    executeCommand(() -> delete(dir[0]));

    for (int i = 0; i < 2; i++) {
      globalUndo();
      dir[0] = myRoot.findChild("dir");
      assertNotNull(dir);
      file[0] = dir[0].findChild("file.txt");
      assertNotNull(file[0]);
      assertEquals("document", getDocumentText(file[0]));

      globalRedo();
      assertNull(myRoot.findChild("dir"));
    }
  }

  public void testUndoDirectoryMovingToExistingDirectory() throws IOException {
    createTestProjectStructureAndMoveDirectory();

    File ioDir = createDirOnTheDirToMovePlaceWithTheSameName();

    checkSuccessfulMoveUndo(ioDir);
  }

  public void testUndoDirectoryMovingToExistingFile() throws IOException {
    createTestProjectStructureAndMoveDirectory();

    File ioDir = createFileOnTheDirToMovePlaceWithTheSameName();

    checkSuccessfulMoveUndo(ioDir);
  }

  private void createTestProjectStructureAndMoveDirectory() throws IOException {
    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      createDirectory("parent");
      myDir1 = myDir.createChildDirectory(this, "dir1");
      myDir2 = myDir.createChildDirectory(this, "dir2");
      myDirToMove = myDir1.createChildDirectory(this, DIR_NAME);
      myDirToMove.createChildData(this, "file.txt");
    });


    CommandProcessor.getInstance().executeCommand(myProject, () -> move(myDirToMove, myDir2), "Moving", null);
  }

  private File createFileOnTheDirToMovePlaceWithTheSameName() throws IOException {
    File ioDir = new File(VfsUtilCore.virtualToIoFile(myDir1), DIR_NAME);
    ioDir.createNewFile();

    refreshFileSystem();
    return ioDir;
  }

  private File createDirOnTheDirToMovePlaceWithTheSameName() {
    File ioDir = new File(VfsUtilCore.virtualToIoFile(myDir1), DIR_NAME);
    ioDir.mkdir();

    refreshFileSystem();
    return ioDir;
  }

  private void checkSuccessfulMoveUndo(File ioDir) {
    globalUndo();

    assertTrue(new File(ioDir, "file.txt").exists());
    assertNull(myDir2.findChild(DIR_NAME));
  }

  public void testUndoDirectoryRenamingToExistingDirectory() {
    createTestProjectStructureAndRenameDirectory();

    File ioDir = createDirOnTheDirToRenamePlaceWithTheSameName();

    checkSuccessfulRenameUndo(ioDir);
  }

  public void testUndoDirectoryRenamingToExistingFile() throws IOException {
    createTestProjectStructureAndRenameDirectory();

    File ioDir = createFileOnTheDirToRenamePlaceWithTheSameName();

    checkSuccessfulRenameUndo(ioDir);
  }

  // ignored because some problems with weak references in filedocumentmanager
  public void testCanUndoDocumentAfterExternalChange() throws Exception {
    final VirtualFile f = createChildData(myRoot, "f.txt");

    executeCommand(() -> setDocumentText(f, "doc"));

    Document doc = FileDocumentManager.getInstance().getDocument(f); // prevent weak refs from being collected
    FileDocumentManager.getInstance().saveAllDocuments(); // prevent 'file has been changed dialog'

    File ioFile = new File(f.getPath());
    FileUtil.writeToFile(ioFile, "external".getBytes());
    ioFile.setLastModified(f.getTimeStamp() + 2000);
    LocalFileSystem.getInstance().refresh(false);

    assertEquals("external", getDocumentText(f));

    Editor editor = getEditor(f);

    assertGlobalUndoNotAvailable();
    assertUndoIsAvailable(editor);

    undo(editor);
    assertEquals("doc", getDocumentText(f));
    undo(editor);
    assertEquals("", getDocumentText(f));

    redo(editor);
    assertEquals("doc", getDocumentText(f));
    redo(editor);
    assertEquals("external", getDocumentText(f));
  }

  public void testCanUndoAfterFileWasDeletedAndWhenCreatedExternally() throws IOException {
    VirtualFile f = createChildData(myRoot, "f.txt");

    final VirtualFile finalF = f;
    executeCommand(() -> setDocumentText(finalF, "doc"));

    executeCommand(() -> delete(finalF));

    File ioFile = new File(f.getPath());
    FileUtil.writeToFile(ioFile, "external".getBytes());
    LocalFileSystem.getInstance().refresh(false);

    f = myRoot.findChild("f.txt");
    assertNotNull(f);

    assertEquals("external", getDocumentText(f));

    Editor editor = getEditor(f);

    assertGlobalUndoIsAvailable();
    assertUndoIsAvailable(editor);

    undo(editor);
    assertEquals("doc", getDocumentText(f));
    undo(editor);
    assertEquals("", getDocumentText(f));

    redo(editor);
    assertEquals("doc", getDocumentText(f));

    // todo
    //redo(editor);
    //assertEquals("external", getDocumentText(f));
  }

  public void testDoNotRecordDocumentChangesWhenFileChangedExternallyAndNoChangesRecordedYet() throws IOException {
    VirtualFile f = createChildData(myRoot, "f.txt");

    Document d = FileDocumentManager.getInstance().getDocument(f); // make sure the document is cached.

    File ioFile = new File(f.getPath());
    FileUtil.writeToFile(ioFile, "external".getBytes());
    ioFile.setLastModified(f.getTimeStamp() + 2000);
    LocalFileSystem.getInstance().refresh(false);

    assertEquals("external", getDocumentText(f));

    assertGlobalUndoNotAvailable();
    assertUndoNotAvailable(getEditor(f));
  }

  public void testGlobalUndoIsAvaliableWhenFileChangedExternallyWithForceFlag() {

    String fileName = "f.txt";
    String projectFileName = "proj.txt";
    VirtualFile f = createChildData(myRoot, projectFileName);

    executeCommand(() -> {
      createChildData(myRoot, fileName);

      // Rider case, modify externally and refresh some file in a command
      File ioFile = new File(f.getPath());
      FileUtil.writeToFile(ioFile, "content".getBytes());
      ioFile.setLastModified(f.getTimeStamp() + 2000);

      f.putUserData(UndoConstants.FORCE_RECORD_UNDO, true);
      f.refresh(false, true);
    });

    assertGlobalUndoIsAvailable();

    assertNotNull(myRoot.findChild(fileName));
    globalUndo();
    assertNull(myRoot.findChild(fileName));
  }

  public void testRecordDocumentChangesWhenFileChangedExternallyAndAlreadyHasChanges() throws IOException {
    final VirtualFile f = createChildData(myRoot, "f.txt");

    executeCommand(() -> setDocumentText(f, "doc"));

    assertGlobalUndoNotAvailable();
    assertUndoIsAvailable(getEditor(f));

    undo(getEditor(f)); // clear undo stack, but preserve the change in redo stack

    File ioFile = new File(f.getPath());
    FileUtil.writeToFile(ioFile, "external".getBytes());
    ioFile.setLastModified(f.getTimeStamp() + 2000);
    LocalFileSystem.getInstance().refresh(false);

    assertEquals("external", getDocumentText(f));

    assertGlobalUndoNotAvailable();
    assertUndoIsAvailable(getEditor(f));
  }

  public void testUndoRedoNotAvailableAfterFileWasDeletedExternally() {
    final VirtualFile f1 = createChildData(myRoot, "f1.txt");
    final VirtualFile f2 = createChildData(myRoot, "f2.txt");

    executeCommand(() -> {
      rename(f1, "ff1.txt");
      setDocumentText(f1, "ff1");
    });
    executeCommand(() -> {
      rename(f2, "ff2.txt");
      setDocumentText(f2, "ff2");
    });

    // <---- test point

    executeCommand(() -> {
      rename(f2, "fff2.txt");
      setDocumentText(f2, "fff2");
    });
    executeCommand(() -> {
      rename(f1, "fff1.txt");
      setDocumentText(f1, "fff1");
    });

    globalUndo();
    assertGlobalUndoIsAvailable();
    assertGlobalRedoIsAvailable();

    globalUndo();
    assertGlobalUndoIsAvailable();
    assertGlobalRedoIsAvailable();

    delete(f1); // commands for f1 should be invalidated here

    assertGlobalUndoIsAvailable();
    assertGlobalRedoIsAvailable();

    globalRedo();

    assertGlobalUndoIsAvailable();
    assertGlobalRedoNotAvailable();

    globalUndo();
    globalUndo(); // back to the 'test point'

    assertGlobalUndoNotAvailable();
    assertGlobalRedoIsAvailable();
  }

  public void testCanUndoDocumentsAfterSave() {
    FileDocumentManager dm = FileDocumentManager.getInstance();
    EditorFactory ef = EditorFactory.getInstance();

    VirtualFile f = createChildData(myRoot, "f.java");

    Document d = dm.getDocument(f);
    Editor e = ef.createEditor(d);

    assertEquals("", d.getText());
    typeInText(e, "12345");
    assertEquals("12345", d.getText());

    dm.saveDocument(d);
    undo(e);
    ef.releaseEditor(e);

    assertEquals("", d.getText());
  }

  public void testClearingRedoStackClearsGlobalCommandsCorrectly() {
    final VirtualFile[] f1 = new VirtualFile[1];
    final VirtualFile[] f2 = new VirtualFile[1];
    final VirtualFile[] f3 = new VirtualFile[1];
    executeCommand(() -> {
      f1[0] = createChildData(myRoot, "f1.java");
      f2[0] = createChildData(myRoot, "f2.java");
      f3[0] = createChildData(myRoot, "f3.java");
    });

    for (VirtualFile each : FileEditorManager.getInstance(myProject).getOpenFiles()) {
      FileEditorManager.getInstance(myProject).closeFile(each);
    }

    executeCommand(() -> {
      setDocumentText(f1[0], "f1");
      setDocumentText(f2[0], "f2");
    });

    executeCommand(() -> setDocumentText(f3[0], "f3"));

    undo(getEditor(f3[0]));
    undo(null);
    assertEquals("", getDocumentText(f1[0]));
    assertEquals("", getDocumentText(f3[0]));

    assertRedoIsAvailable(null);
    assertRedoIsAvailable(getEditor(f1[0]));
    assertRedoIsAvailable(getEditor(f2[0]));
    assertRedoIsAvailable(getEditor(f3[0]));

    for (VirtualFile each : FileEditorManager.getInstance(myProject).getOpenFiles()) {
      FileEditorManager.getInstance(myProject).closeFile(each);
    }
    executeCommand(() -> setDocumentText(f1[0], "ff1"));

    assertRedoNotAvailable(null);
    assertRedoNotAvailable(getEditor(f1[0]));
    assertRedoNotAvailable(getEditor(f2[0]));
    assertRedoIsAvailable(getEditor(f3[0]));
  }

  public void testRegisteringOpenDocumentWhenAnotherFileIsAffected() {
    final VirtualFile[] f1 = new VirtualFile[1];
    final VirtualFile[] f2 = new VirtualFile[1];
    final VirtualFile[] f3 = new VirtualFile[1];
    f1[0] = createChildData(myRoot, "f1.java");
    f2[0] = createChildData(myRoot, "f2.java");
    f3[0] = createChildData(myRoot, "f3.java");

    executeCommand(() -> setDocumentText(f1[0], "f1"));
    assertUndoNotAvailable(null);
    assertUndoIsAvailable(getEditor(f1[0]));
    assertUndoNotAvailable(getEditor(f2[0]));
    assertUndoNotAvailable(getEditor(f3[0]));

    myEditor = openEditor(f2[0]);

    executeCommand(() -> setDocumentText(f1[0], "f1"));
    assertUndoIsAvailable(null);
    assertUndoIsAvailable(getEditor(f1[0]));
    assertUndoIsAvailable(getEditor(f2[0]));
    assertUndoNotAvailable(getEditor(f3[0]));
  }

  public void testRegisteringOpenDocumentForReadOnlyFileDoesNotBreakUndoChain() throws Exception {
    final VirtualFile f1 = createChildData(myRoot, "f1.java");
    final VirtualFile f2 = createChildData(myRoot, "f2.java");
    final VirtualFile f3 = createChildData(myRoot, "f3.java");

    WriteAction.runAndWait(() -> f2.setWritable(false));

    executeCommand(() -> setDocumentText(f1, "initial"));
    assertUndoNotAvailable(null);
    assertUndoIsAvailable(getEditor(f1));
    assertUndoNotAvailable(getEditor(f2));
    assertUndoNotAvailable(getEditor(f3));

    myEditor = openEditor(f2);

    executeCommand(() -> setDocumentText(f1, "new content"));
    assertUndoIsAvailable(null);
    assertUndoIsAvailable(getEditor(f1));
    assertUndoIsAvailable(getEditor(f2));
    assertUndoNotAvailable(getEditor(f3));

    undo(getEditor(f2));
    assertEquals("initial", getDocumentText(f1));
  }

  public void testDoNotRegisterOpenDocumentWhenAnotherFileReloaded() throws Exception {
    final VirtualFile f1 = createChildData(myRoot, "f1.java");
    final VirtualFile f2 = createChildData(myRoot, "f2.java");

    executeCommand(() -> setDocumentText(f1, "f1"));
    executeCommand(() -> {
      setDocumentText(f2, "f2");
      FileDocumentManager.getInstance().saveDocument(getDocument(f2));
    });

    assertUndoNotAvailable(null);
    assertUndoIsAvailable(getEditor(f1));
    assertUndoIsAvailable(getEditor(f2));

    myEditor = openEditor(f1);

    File file = new File(f2.getPath());
    FileUtil.writeToFile(file, "reloaded");
    file.setLastModified(file.lastModified() + 10000);
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singletonList(file));

    assertUndoNotAvailable(null);
    assertUndoIsAvailable(getEditor(f1));
    assertUndoIsAvailable(getEditor(f2));

    // undo open document first
    undo(getEditor(f1));
    assertEquals("", getDocumentText(f1));

    undo(getEditor(f2));
    assertEquals("f2", getDocumentText(f2));
  }

  public void testClearingRedoStackClearsGlobalCommandsCorrectlyIntersectingStacks() {
    final VirtualFile[] f1 = new VirtualFile[1];
    final VirtualFile[] f2 = new VirtualFile[1];
    final VirtualFile[] f3 = new VirtualFile[1];
    final VirtualFile[] f4 = new VirtualFile[1];
    executeCommand(() -> {
      f1[0] = createChildData(myRoot, "f1.java");
      f2[0] = createChildData(myRoot, "f2.java");
      f3[0] = createChildData(myRoot, "f3.java");
      f4[0] = createChildData(myRoot, "f4.java");
    });

    executeCommand(() -> {
      setDocumentText(f1[0], "f1");
      setDocumentText(f2[0], "f2");
    });

    executeCommand(() -> {
      setDocumentText(f3[0], "f3");
      setDocumentText(f4[0], "f4");
    });

    undo(null);
    undo(null);
    assertEquals("", getDocumentText(f1[0]));
    assertEquals("", getDocumentText(f3[0]));

    assertRedoIsAvailable(null);
    assertRedoIsAvailable(getEditor(f1[0]));
    assertRedoIsAvailable(getEditor(f2[0]));
    assertRedoIsAvailable(getEditor(f3[0]));
    assertRedoIsAvailable(getEditor(f4[0]));

    executeCommand(() -> setDocumentText(f1[0], "ff1"));

    assertRedoNotAvailable(null);
    assertRedoNotAvailable(getEditor(f1[0]));
    assertRedoNotAvailable(getEditor(f2[0]));
    assertRedoNotAvailable(getEditor(f3[0]));
    assertRedoNotAvailable(getEditor(f4[0]));
  }

  public void testClearingRedoStackClearsStackNotTooMuch() {
    final VirtualFile[] f1 = new VirtualFile[1];
    final VirtualFile[] f2 = new VirtualFile[1];
    executeCommand(() -> {
      f1[0] = createChildData(myRoot, "f1.java");
      f2[0] = createChildData(myRoot, "f2.java");
    });

    executeCommand(() -> setDocumentText(f1[0], "f1"));

    executeCommand(() -> {
      setDocumentText(f1[0], "ff1");
      setDocumentText(f2[0], "ff2");
    });

    undo(getEditor(f1[0]));
    undo(getEditor(f1[0]));
    assertEquals("", getDocumentText(f1[0]));
    assertEquals("", getDocumentText(f2[0]));

    assertRedoIsAvailable(null);
    assertRedoIsAvailable(getEditor(f1[0]));
    assertRedoIsAvailable(getEditor(f2[0]));

    executeCommand(() -> setDocumentText(f2[0], "fff2"));

    assertRedoNotAvailable(null);
    assertRedoIsAvailable(getEditor(f1[0]));
    assertRedoNotAvailable(getEditor(f2[0]));

    redo(getEditor(f1[0]));
    assertEquals("f1", getDocumentText(f1[0]));
  }

  public void testAddingAffectedDocumentOrFile() {
    final VirtualFile f1 = createChildData(myRoot, "f1.java");
    final VirtualFile f2 = createChildData(myRoot, "f2.java");
    final VirtualFile f3 = createChildData(myRoot, "f3.java");

    assertUndoNotAvailable(getEditor(f1));
    assertUndoNotAvailable(getEditor(f2));
    assertUndoNotAvailable(getEditor(f3));
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      setDocumentText(f1, "foo");
      CommandProcessor.getInstance().addAffectedDocuments(myProject, FileDocumentManager.getInstance().getDocument(f2));
      CommandProcessor.getInstance().addAffectedFiles(myProject, f3);
    }, null, null);

    assertUndoIsAvailable(getEditor(f1));
    assertUndoIsAvailable(getEditor(f2));
    assertUndoIsAvailable(getEditor(f3));

    myManager.flushCurrentCommandMerger();

    assertUndoIsAvailable(getEditor(f1));
    assertUndoIsAvailable(getEditor(f2));
    assertUndoIsAvailable(getEditor(f3));
  }

  public void testAddingAffectedDocumentWhenNoOtherChangesDoesntChangeUndoRedoStacks() {
    final VirtualFile f1 = createChildData(myRoot, "f1.java");
    final VirtualFile f2 = createChildData(myRoot, "f2.java");
    final VirtualFile f3 = createChildData(myRoot, "f3.java");

    assertUndoNotAvailable(getEditor(f1));
    assertUndoNotAvailable(getEditor(f2));
    assertUndoNotAvailable(getEditor(f3));
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      CommandProcessor.getInstance().addAffectedDocuments(myProject, FileDocumentManager.getInstance().getDocument(f2));
      CommandProcessor.getInstance().addAffectedFiles(myProject, f3);
    }, null, null);

    assertUndoNotAvailable(getEditor(f1));
    assertUndoNotAvailable(getEditor(f2));
    assertUndoNotAvailable(getEditor(f3));

    myManager.flushCurrentCommandMerger();

    assertUndoNotAvailable(getEditor(f1));
    assertUndoNotAvailable(getEditor(f2));
    assertUndoNotAvailable(getEditor(f3));
  }

  public void testFixRIDER10340() {
    createClass("TestClass1");
    Editor editor = openEditor("TestClass1.java");

    WriteAction.runAndWait(() -> executeCommand(() -> editor.getDocument().insertString(27, "public class Aaa {}")));

    PsiDocumentManager instance = PsiDocumentManager.getInstance(myProject);
    instance.commitAllDocuments();
    PsiFile file = instance.getPsiFile(editor.getDocument());
    PsiClass aaaClass = (PsiClass)(file.getViewProvider().findElementAt(41).getParent());

    IntentionAction fix = QuickFixFactory.getInstance().createMoveClassToSeparateFileFix(aaaClass);
    WriteAction.runAndWait(() -> executeCommand(() -> fix.invoke(myProject, editor, file)));
    instance.commitAllDocuments();

    VirtualFile aaaFile = file.getVirtualFile().getParent().findChild("Aaa.java");
    deleteInCommand(aaaFile);

    undo(editor);

    undo(editor);

    assertEquals("public class TestClass1 {\n}public class Aaa {}\n", editor.getDocument().getText());
  }

  public void testPerformance() {
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile dir1 = myRoot.createChildDirectory(this, "dir1");
          VirtualFile dir2 = myRoot.createChildDirectory(this, "dir2");
          dir1.createChildData(this, "f.java");
          dir1.move(this, dir2);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });

    PlatformTestUtil.assertTiming("", 2000, 1, () -> {
      redoUndo();
      redoUndo();
      redoUndo();
    });
  }

  private void redoUndo() {
    globalUndo();
    globalRedo();
  }

  public void testRedoIsNotAvailableAfterFileChanges() {
    final VirtualFile f = createChildData(myRoot, "f.java");

    renameInCommand(f, "ff.java");

    globalUndo();
    assertGlobalRedoIsAvailable();

    renameInCommand(f, "fff.java");

    assertGlobalRedoNotAvailable();
  }

  public void testUndoRedoFileWithChangedDocument() throws Exception {
    File directory = createTempDirectory(true);
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(directory);
    assertNotNull(file);
    final String[] path = new String[1];
    executeCommand(() -> {
      PsiFile f = createFile(myModule, file, "foo.txt", "initial");
      Document doc = PsiDocumentManager.getInstance(myProject).getDocument(f);
      setDocumentText(doc, "document");
      path[0] = f.getVirtualFile().getPath();
    });

    globalUndo();
    globalRedo();

    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(path[0]);
    assertNotNull(f);
    assertEquals("initial", VfsUtilCore.loadText(f));
    Document doc = FileDocumentManager.getInstance().getDocument(f);
    assertEquals("document", doc.getText());
  }

  public void testUndoRedoFileWithChangedDocumentWithSeveralAffectedFiles() throws Exception {
    final String[] path = new String[2];
    executeCommand(() -> {
      VirtualFile f = createChildData(myRoot, "foo1.txt");
      setBinaryContent(f, "initial1".getBytes());
      Document doc = FileDocumentManager.getInstance().getDocument(f);
      setDocumentText(doc, "document1");
      path[0] = f.getPath();

      f = createChildData(myRoot, "foo2.txt");
      setBinaryContent(f, "initial2".getBytes());
      doc = FileDocumentManager.getInstance().getDocument(f);
      setDocumentText(doc, "document2");
      path[1] = f.getPath();
    });

    globalUndo();
    globalRedo();

    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(path[0]);
    assertNotNull(f);
    assertEquals("initial1", VfsUtilCore.loadText(f));
    Document doc = FileDocumentManager.getInstance().getDocument(f);
    assertEquals("document1", doc.getText());

    f = LocalFileSystem.getInstance().findFileByPath(path[1]);
    assertNotNull(f);
    assertEquals("initial2", VfsUtilCore.loadText(f));
    doc = FileDocumentManager.getInstance().getDocument(f);
    assertEquals("document2", doc.getText());
  }

  private static void setDocumentText(final Document doc, final String document2) {
    ApplicationManager.getApplication().runWriteAction(() -> doc.setText(document2));
  }

  public void testUndoRedoFileMoveAndDeleteWithChangedDocument() throws Exception {
    final String[] path = new String[1];
    final VirtualFile[] dir = new VirtualFile[1];
    final VirtualFile[] f = new VirtualFile[1];

    executeCommand(() -> {
      dir[0] = createChildDirectory(myRoot, "dir");
      f[0] = createChildData(myRoot, "foo.txt");
      setBinaryContent(f[0], "initial".getBytes());
      setDocumentText(f[0], "document");
      path[0] = f[0].getPath();
    });
    executeCommand(() -> {
      move(f[0], dir[0]);
      setDocumentText(f[0], "moved");
    });
    executeCommand(() -> delete(dir[0]));

    globalUndo();

    f[0] = LocalFileSystem.getInstance().findFileByPath(f[0].getPath());
    dir[0] = LocalFileSystem.getInstance().findFileByPath(dir[0].getPath());
    assertNotNull(f[0]);
    assertNotNull(dir[0]);
    assertEquals(dir[0], f[0].getParent());
    assertEquals("moved", VfsUtilCore.loadText(f[0]));
    Document doc = FileDocumentManager.getInstance().getDocument(f[0]);
    assertEquals("moved", doc.getText());

    globalUndo();

    assertEquals(myRoot, f[0].getParent());
    assertEquals("moved", VfsUtilCore.loadText(f[0]));
    doc = FileDocumentManager.getInstance().getDocument(f[0]);
    assertEquals("document", doc.getText());

    globalUndo();
    globalRedo();

    f[0] = LocalFileSystem.getInstance().findFileByPath(f[0].getPath());
    dir[0] = LocalFileSystem.getInstance().findFileByPath(dir[0].getPath());

    assertEquals(myRoot, f[0].getParent());
    assertEquals("initial", VfsUtilCore.loadText(f[0]));
    doc = FileDocumentManager.getInstance().getDocument(f[0]);
    assertEquals("document", doc.getText());

    globalRedo();

    assertEquals(dir[0], f[0].getParent());
    assertEquals("initial", VfsUtilCore.loadText(f[0]));
    doc = FileDocumentManager.getInstance().getDocument(f[0]);
    assertEquals("moved", doc.getText());
  }

  private void renameInCommand(final VirtualFile f, final String name) {
    executeCommand(() -> rename(f, name));
  }

  private void checkSuccessfulRenameUndo(File ioDir) {
    try {
      globalUndo();
    }
    catch (Exception ex) {
      fail("Unexpected exception: " + ex.getLocalizedMessage());
    }

    assertTrue(new File(ioDir, "file.txt").exists());
    assertNull(myDir.findChild(NEW_NAME));
  }

  private File createDirOnTheDirToRenamePlaceWithTheSameName() {
    File result = new File(VfsUtilCore.virtualToIoFile(myDirToRename.getParent()), DIR_TO_RENAME_NAME);
    result.mkdir();
    refreshFileSystem();
    return result;
  }

  private File createFileOnTheDirToRenamePlaceWithTheSameName() throws IOException {
    File result = new File(VfsUtilCore.virtualToIoFile(myDirToRename.getParent()), DIR_TO_RENAME_NAME);
    result.createNewFile();
    refreshFileSystem();
    return result;
  }

  private void createTestProjectStructureAndRenameDirectory() {
    createDirectory("parent");
    myDirToRename = createChildDirectory(myDir, DIR_TO_RENAME_NAME);
    createChildData(myDirToRename, "file.txt");
    CommandProcessor.getInstance().executeCommand(myProject, () -> rename(myDirToRename, NEW_NAME), "Renaming", null);
  }

  private static void refreshFileSystem() {
    VirtualFileManager.getInstance().syncRefresh();
  }

  private void deleteInCommand(final VirtualFile f) {
    executeCommand((Command)() -> delete(f), "Delete file");
  }

  private void checkDirDoesNotExist() {
    assertNull(myRoot.findChild(myDirName));
  }

  private void checkDirExists() {
    VirtualFile file = myRoot.findChild(myDirName);
    assertNotNull(file);
    assertTrue(file.isValid());
  }

  private void createDirectory(final String name) {
    executeCommand((Command)() -> myDir = createChildDirectory(myRoot, name), "Create Directory");
  }

  private void deleteDirectory() {
    Command command = () -> delete(myDir);
    executeCommand(command, "Delete Directory");
  }

  private void moveClassTo(final VirtualFile dirTo) {
    Command command = () -> {
      VirtualFile file = myClass.getContainingFile().getVirtualFile();
      move(file, dirTo);
    };
    executeCommand(command, "Move class to a new dir");
  }

  public void testSCR5784() throws Exception {
    myFile = createFile("Test.java", "abcd efgh ijk");
    myEditor = createEditor(myFile.getVirtualFile());

    myManager.setEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor() {
        return TextEditorProvider.getInstance().getTextEditor(myEditor);
      }
    });

    final SelectionModel selectionModel = myEditor.getSelectionModel();
    final CaretModel caretModel = myEditor.getCaretModel();
    final Document document = myEditor.getDocument();

    selectionModel.setSelection(0, 4);
    caretModel.moveToOffset(4);

    String text0 = document.getText();
    int caret0 = caretModel.getOffset();
    int selStart0 = selectionModel.getSelectionStart();
    int selEnd0 = selectionModel.getSelectionEnd();

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      selectionModel.removeSelection();
      document.insertString(document.getTextLength(), "abcd");
      selectionModel.setSelection(document.getTextLength() - "abcd".length(), document.getTextLength());
    }), "Command 1", "DndGroup");

    CommandProcessor.getInstance()
      .executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> document.deleteString(0, "abcd".length())),
                      "Command 2", "DndGroup");

    CommandProcessor.getInstance().executeCommand(myProject, EmptyRunnable.getInstance(), "Command 3", null);

    String text1 = document.getText();
    int caret1 = caretModel.getOffset();
    int selStart1 = selectionModel.getSelectionStart();
    int selEnd1 = selectionModel.getSelectionEnd();

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      selectionModel.removeSelection();
      document.insertString(4, "abcd");
      selectionModel.setSelection(4, 4 + "abcd".length());
    }), "Command 4", "DndGroup");

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication()
                                                    .runWriteAction(() -> document.deleteString(document.getTextLength() - "abcd".length(), document.getTextLength())), "Command 5",
                                                  "DndGroup");

    undo(myEditor);

    assertEquals(text1, document.getText());
    assertEquals(caret1, caretModel.getOffset());
    assertEquals(selStart1, selectionModel.getSelectionStart());
    assertEquals(selEnd1, selectionModel.getSelectionEnd());

    undo(myEditor);

    assertEquals(text0, document.getText());
    assertEquals(caret0, caretModel.getOffset());
    assertEquals(selStart0, selectionModel.getSelectionStart());
    assertEquals(selEnd0, selectionModel.getSelectionEnd());
  }

  public void testEditorWithSeveralDocumentsUndo() {
    final VirtualFile file1 = createChildData(myRoot, "file1.txt");
    final VirtualFile file2 = createChildData(myRoot, "file2.txt");

    final Document document1 = FileDocumentManager.getInstance().getDocument(file1);
    final Document document2 = FileDocumentManager.getInstance().getDocument(file2);

    Mock.MyFileEditor fileEditor = new Mock.MyFileEditor();
    fileEditor.DOCUMENTS = new Document[]{document1, document2};

    UndoManager undoManager = UndoManager.getInstance(myProject);

    CommandProcessor.getInstance()
      .executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> document2.replaceString(0, 0, "text2")),
                      "test_command", null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);

    assertTrue(undoManager.isUndoAvailable(fileEditor));

    undoManager.undo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("", document1.getText());
    assertEquals("", document2.getText());

    undoManager.redo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("", document1.getText());
    assertEquals("text2", document2.getText());

    changeText(document1, "");
    changeText(document2, "");

    changeText(document1, "text1");
    changeText(document2, "text2");
    assertTrue(undoManager.isUndoAvailable(fileEditor));

    undoManager.undo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("text1", document1.getText());
    assertEquals("", document2.getText());

    undoManager.undo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("", document1.getText());
    assertEquals("", document2.getText());

    undoManager.redo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("text1", document1.getText());
    assertEquals("", document2.getText());

    undoManager.redo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("text1", document1.getText());
    assertEquals("text2", document2.getText());

    //check documents changes backward

    changeText(document1, "");
    changeText(document2, "");

    changeText(document2, "text2");
    changeText(document1, "text1");
    assertTrue(undoManager.isUndoAvailable(fileEditor));

    undoManager.undo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("text2", document2.getText());
    assertEquals("", document1.getText());

    undoManager.undo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("", document1.getText());
    assertEquals("", document2.getText());

    undoManager.redo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("", document1.getText());
    assertEquals("text2", document2.getText());

    undoManager.redo(fileEditor);
    assertFalse(myConfirmationWasRequested);
    assertEquals("text1", document1.getText());
    assertEquals("text2", document2.getText());
  }

  public void testMergingSeveralDocumentCommands() {
    final VirtualFile[] files = new VirtualFile[3];
    files[0] = createChildData(myRoot, "f1.txt");
    files[1] = createChildData(myRoot, "f2.txt");
    files[2] = createChildData(myRoot, "f3.txt");

    executeCommand(() -> setDocumentText(files[0], "text1"), "command", "ID");

    executeCommand(() -> setDocumentText(files[1], "text2"), "command", "ID");

    executeCommand(() -> setDocumentText(files[2], "text3"), "command", "ID");

    //assertGlobalUndoIsAvailable();

    assertUndoIsAvailable(getEditor(files[0]));
    assertUndoIsAvailable(getEditor(files[1]));
    assertUndoIsAvailable(getEditor(files[2]));

    //globalUndo();
    undo(getEditor(files[0]));

    assertEquals("", getDocumentText(files[0]));
    assertEquals("", getDocumentText(files[1]));
    assertEquals("", getDocumentText(files[2]));

    //assertGlobalUndoNotAvailable();
    assertUndoNotAvailable(getEditor(files[0]));
    assertUndoNotAvailable(getEditor(files[1]));
    assertUndoNotAvailable(getEditor(files[2]));

    //assertGlobalRedoIsAvailable();
    assertRedoIsAvailable(getEditor(files[0]));
    assertRedoIsAvailable(getEditor(files[1]));
    assertRedoIsAvailable(getEditor(files[2]));

    //globalRedo();
    redo(getEditor(files[0]));

    assertEquals("text1", getDocumentText(files[0]));
    assertEquals("text2", getDocumentText(files[1]));
    assertEquals("text3", getDocumentText(files[2]));
  }

  public void testUndoConfirmationPolicy() {
    doTest(UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, false);
    doTest(UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, UndoConfirmationPolicy.REQUEST_CONFIRMATION, true);
    doTest(UndoConfirmationPolicy.REQUEST_CONFIRMATION, UndoConfirmationPolicy.REQUEST_CONFIRMATION, true);
    doTest(UndoConfirmationPolicy.REQUEST_CONFIRMATION, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, true);

    doTest(UndoConfirmationPolicy.DEFAULT, UndoConfirmationPolicy.DEFAULT, true);
    doTest(UndoConfirmationPolicy.REQUEST_CONFIRMATION, UndoConfirmationPolicy.DEFAULT, true);
    doTest(UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, UndoConfirmationPolicy.DEFAULT, false);
    doTest(UndoConfirmationPolicy.DEFAULT, UndoConfirmationPolicy.REQUEST_CONFIRMATION, true);
    doTest(UndoConfirmationPolicy.DEFAULT, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, false);
  }

  private void doTest(UndoConfirmationPolicy policy1, UndoConfirmationPolicy policy2, boolean expected) {
    final VirtualFile file1 = createChildData(myRoot, "file1.txt");
    final VirtualFile file2 = createChildData(myRoot, "file2.txt");

    final Document document1 = FileDocumentManager.getInstance().getDocument(file1);
    final Document document2 = FileDocumentManager.getInstance().getDocument(file2);

    String groupId = "ID";

    changeText(document1, "text1", policy1, groupId);
    changeText(document2, "text2", policy2, groupId);

    UndoManager undoManager = UndoManager.getInstance(myProject);

    Mock.MyFileEditor fileEditor = new Mock.MyFileEditor();
    fileEditor.DOCUMENTS = new Document[]{document1, document2};

    assertTrue(undoManager.isUndoAvailable(fileEditor));

    undoManager.undo(fileEditor);

    assertEquals(expected, myConfirmationWasRequested);

    assertEquals("", document1.getText());
    assertEquals("", document2.getText());

    delete(file1);
    delete(file2);

    myConfirmationWasRequested = false;
  }

  private void changeText(final Document document1, final String text) {
    changeText(document1, text, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, null);
  }

  private void changeText(final Document document1, final String text, UndoConfirmationPolicy policy, String groupId) {
    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication()
      .runWriteAction(() -> document1.replaceString(0, document1.getTextLength(), text)), "test_command", groupId, policy);
  }

  protected void assertFileExists(String fileName, String content) {
    assertFileExists(myRoot, fileName, content);
  }

  private void assertFileExists(VirtualFile dir, String fileName, String content) {
    assertFileExists(dir, fileName);
    assertEquals(content, getDocumentText(findFile(fileName, dir)));
  }

  private void assertFileDoesNotExist(String fileName, VirtualFile dir) {
    assertNull(findFile(fileName, dir));
  }

  private void assertFileExists(VirtualFile dir, String fileName) {
    StoreUtil.saveDocumentsAndProjectsAndApp(false);
    assertNotNull(findFile(fileName, dir));
  }

  protected VirtualFile findFile(String className, VirtualFile dir) {
    return dir.findChild(className + ".java");
  }

  public void setDocumentText(final VirtualFile f, final String text) {
    ApplicationManager.getApplication().runWriteAction(() -> FileDocumentManager.getInstance().getDocument(f).setText(text));
  }

  public String getDocumentText(VirtualFile f) {
    return FileDocumentManager.getInstance().getDocument(f).getText();
  }

  private void checkAllFilesDeleted() {
    assertEquals(0, myRoot.getChildren().length);
  }

  private Editor openEditor(String fileName) {
    return openEditor(myRoot.findChild(fileName));
  }

  private Editor openEditor(VirtualFile file) {
    assertNotNull(file);
    return FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, file), false);
  }

  protected void deleteClass() {
    executeCommand((Command)() -> ApplicationManager.getApplication().runWriteAction(() -> myClass.delete()), "Delete Class");
  }

  protected void createClass(@NonNls final String name) {
    createClass(name, myRoot);
  }

  protected PsiJavaFile createClass(final String name, final VirtualFile dir) {
    executeCommand((Command)() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        myClass = JavaDirectoryService.getInstance().createClass(myPsiManager.findDirectory(dir), name);
        myClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myClass);
      });

      myContainingFile = (PsiJavaFile)myClass.getContainingFile();
    }, "Create Class" + name);
    return myContainingFile;
  }
}
