// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.file.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.file.impl.FileManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.*;
import com.intellij.util.WaitFor;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("ConstantConditions")
@SkipSlowTestLocally
public class PsiEventsTest extends JavaPsiTestCase {
  private VirtualFile myPrjDir1;
  private VirtualFile myPrjDir2;
  private VirtualFile mySrcDir1;
  private VirtualFile mySrcDir2;
  private VirtualFile mySrcDir3;
  private VirtualFile myClsDir1;
  private VirtualFile myIgnoredDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    File root = createTempDirectoryWithSuffix(null).toFile();

    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);

      myPrjDir1 = createChildDirectory(rootVFile, "prj1");
      mySrcDir1 = createChildDirectory(myPrjDir1, "src1");
      mySrcDir2 = createChildDirectory(myPrjDir1, "src2");

      myPrjDir2 = createChildDirectory(rootVFile, "prj2");
      mySrcDir3 = myPrjDir2;


      myClsDir1 = createChildDirectory(myPrjDir1, "cls1");

      myIgnoredDir = createChildDirectory(mySrcDir1, "CVS");

      PsiTestUtil.addContentRoot(myModule, myPrjDir1);
      PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
      PsiTestUtil.addSourceRoot(myModule, mySrcDir2);
      PsiTestUtil.addContentRoot(myModule, myPrjDir2);
      ModuleRootModificationUtil.addModuleLibrary(myModule, myClsDir1.getUrl());
      PsiTestUtil.addSourceRoot(myModule, mySrcDir3);
    });
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
  }

  public void testCreateFile() {
    FileManager fileManager = myPsiManager.getFileManager();
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiDirectory psiDir = fileManager.findDirectory(myPrjDir1);
    createChildData(myPrjDir1, "a.txt");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(psiDir.getName(), expected, string);
  }

  public void testCreateDirectory() {
    FileManager fileManager = myPsiManager.getFileManager();
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiDirectory psiDir = fileManager.findDirectory(myPrjDir1);
    createChildDirectory(myPrjDir1, "aaa");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(psiDir.getName(), expected, string);
  }

  public void testDeleteFile() {
    VirtualFile file = createChildData(myPrjDir1, "a.txt");

    FileManager fileManager = myPsiManager.getFileManager();
    PsiFile psiFile = fileManager.findFile(file);//it's important to hold the reference

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    delete(file);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(psiFile.getName(), expected, string);
  }

  public void testDeleteDirectory() {
    VirtualFile file = createChildDirectory(myPrjDir1, "aaa");

    FileManager fileManager = myPsiManager.getFileManager();
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    delete(file);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(psiDirectory.getName(), expected, string);
  }

  public void testRenameFile() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    PsiDirectory directory = fileManager.findDirectory(myPrjDir1);
    assertNotNull(directory);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "b.txt");

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange fileName
        propertyChanged fileName
        """;
    assertEquals(psiFile.getName(), expected, string);
  }

  public void testRenameFileWithoutDir() {
    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    GCWatcher.tracking(fileManager.getCachedDirectory(myPrjDir1)).ensureCollected();

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "b.txt");

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange fileName
        propertyChanged fileName
        """;
    assertEquals(psiFile.getName(), expected, string);
  }

  public void testRenameFileChangingExtension() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "b.xml");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildReplacement
        childReplaced
        """;
    assertEquals(psiFile.getName(), expected, string);
  }

  public void testRenameFileToIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "CVS");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(psiFile.getName(), expected, string);
    assertNull(fileManager.findFile(file));
  }

  public void testRenameFileFromIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "CVS");
    PsiDirectory psiDirectory = fileManager.findDirectory(file.getParent());

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "aaa.txt");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(psiDirectory.getName(), expected, string);
  }

  public void testRenameDirectory_WithPsiDir() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir1");
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "dir2");

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange directoryName
        propertyChanged directoryName
        """;
    assertEquals(psiDirectory.getName(), expected, string);
  }

  public void testRenameDirectory_WithoutPsiDir() {
    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir1");

    GCWatcher.tracking(fileManager.getCachedDirectory(file)).ensureCollected();

    assertNull(fileManager.getCachedDirectory(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "dir2");

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange propUnloadedPsi
        propertyChanged propUnloadedPsi
        """;
    assertEquals(fileManager.findDirectory(file).getName(), expected, string);
  }

  public void testRenameDirectoryToIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir1");
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "CVS");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(psiDirectory.getName(), expected, string);
    assertNull(fileManager.findDirectory(file));
  }

  public void testRenameDirectoryFromIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "CVS");
    PsiDirectory psiDirectory = fileManager.findDirectory(file.getParent());

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "dir");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(psiDirectory.getName(), expected, string);
  }

  public void testMakeFileReadOnly() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    final EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, IOException>)() -> {
      ReadOnlyAttributeUtil.setReadOnlyAttribute(file, true);
      return null;
    });


    final String expected =
      """
        beforePropertyChange writable
        propertyChanged writable
        """;

    new WaitFor(500){
      @Override
      protected boolean condition() {
        return expected.equals(listener.getEventsString());
      }
    }.assertCompleted(listener.getEventsString());

    ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, IOException>)() -> {
      ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false);
      return null;
    });
  }

  public void testMoveFile() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    move(file, myPrjDir1.getParent());

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildMovement
        childMoved
        """;
    assertEquals(psiFile.getName(), expected, string);
  }

  public void testMoveFileToIgnoredDir() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    move(file, myIgnoredDir);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(psiFile.getName(), expected, string);
    assertNull(fileManager.findFile(file));
  }

  public void testMoveFileFromIgnoredDir() {
    VirtualFile file = createChildData(myIgnoredDir, "a.txt");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    move(file, myPrjDir1);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(expected, string);
  }

  public void testMoveFileInsideIgnoredDir() {
    VirtualFile file = createChildData(myIgnoredDir, "a.txt");
    VirtualFile subdir = createChildDirectory(myIgnoredDir, "subdir");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    move(file, subdir);

    String string = listener.getEventsString();
    String expected = "";
    assertEquals(expected, string);
  }

  public void testMoveDirectory() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir");
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    move(file, myPrjDir1.getParent());

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildMovement
        childMoved
        """;
    assertEquals(psiDirectory.getName(), expected, string);
  }

  public void testMoveDirectoryToIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir");
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    move(file, myIgnoredDir);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(psiDirectory.getName(), expected, string);
    assertNull(fileManager.findDirectory(file));
  }

  public void testMoveDirectoryFromIgnored() {
    VirtualFile file = createChildDirectory(myIgnoredDir, "dir");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    move(file, myPrjDir1);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(expected, string);
  }

  public void testMoveDirectoryInsideIgnored() {
    VirtualFile file = createChildDirectory(myIgnoredDir, "dir");
    VirtualFile subdir = createChildDirectory(myIgnoredDir, "subdir");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    move(file, subdir);

    String string = listener.getEventsString();
    String expected = "";
    assertEquals(expected, string);
  }

  public void testChangeFile() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    setFileText(file, "aaa");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiFile psiFile = fileManager.findFile(file);
    assertNotNull(psiFile.getText()); // Trigger PSI loading

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    setFileText(file, "bbb");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    /*
    assertEquals("", listener.getEventsString());

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    */

    assertEquals(
      """
        beforeChildrenChange
        beforeChildReplacement
        childReplaced
        childrenChanged
        """,
            listener.getEventsString());
  }

  public void testAddExcludeRoot() {
    final VirtualFile dir = createChildDirectory(myPrjDir1, "aaa");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiTestUtil.addExcludedRoot(myModule, dir);


    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange roots
        propertyChanged roots
        """;
    assertEquals(expected, string);
  }

  public void testAddSourceRoot() {
    final VirtualFile dir = createChildDirectory(myPrjDir1, "aaa");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiTestUtil.addSourceRoot(myModule, dir);

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange roots
        propertyChanged roots
        """;
    assertEquals(expected, string);
  }

  public void testModifyFileTypes() {
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    ApplicationManager.getApplication().runWriteAction(() -> {
      FileTypeManagerEx fileTypeManagerEx = (FileTypeManagerEx)FileTypeManager.getInstance();
      fileTypeManagerEx.makeFileTypesChange("psi events test", EmptyRunnable.getInstance());
    });


    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange propFileTypes
        propertyChanged propFileTypes
        """;
    assertEquals(expected, string);
  }

  public void testCyclicDispatching() throws Throwable {
    final VirtualFile virtualFile = createFile("a.xml", "<tag/>").getVirtualFile();
    final PsiTreeChangeAdapter listener = new PsiTreeChangeAdapter() {
      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        getJavaFacade().findClass("XXX", GlobalSearchScope.allScope(myProject));
      }
    };
    getPsiManager().addPsiTreeChangeListener(listener,getTestRootDisposable());
    rename(virtualFile, "b.xml");
  }

  private String original;
  private String eventsFired = "";
  private PsiTreeChangeListener listener;
  public void testBeforeAfterChildrenChange() throws Throwable {
    listener = new PsiTreeChangeListener() {
      @Override
      public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
      }

      @Override
      public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
      }

      @Override
      public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
      }

      @Override
      public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
      }

      @Override
      public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
      }

      @Override
      public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
      }

      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
        assertBeforeEventFired(event);
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
        assertBeforeEventFired(event);
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
        assertBeforeEventFired(event);
      }

      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
        assertBeforeEventFired(event);
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
        assertBeforeEventFired(event);
      }

      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        logEvent(event);
        assertBeforeEventFired(event);
      }
    };

    myFile = createFile("A.java", "class A { int i; }");
    doTestEvents("class A { }");
    //doTestEvents("class A { int i; int j; }"); //todo: f*(&&ing compactChanges() in TreeChangeEventImpl garbles afterXXX events so that they don't match beforeXXX
    doTestEvents("class A { int k; }");
    doTestEvents("class A { int k; int i; }");
    doTestEvents("class A { void foo(){} }");
    doTestEvents("xxxxxx");
    doTestEvents("");
  }

  private void logEvent(PsiTreeChangeEvent event) {
    PsiTreeChangeEventImpl.PsiEventType code = ((PsiTreeChangeEventImpl)event).getCode();
    eventsFired += eventText(event, code);
  }

  private static String eventText(PsiTreeChangeEvent event, PsiTreeChangeEventImpl.PsiEventType code) {
    PsiElement parent = event.getParent();
    PsiElement oldChild = event.getOldChild();
    if (oldChild == null) oldChild = event.getChild();
    PsiElement newChild = event.getNewChild();
    return code + ":" +
           (parent == null ? null : parent.getNode().getElementType()) + "/" +
           (oldChild == null ? null : oldChild.getNode().getElementType()) + "->" +
           (newChild == null ? null : newChild.getNode().getElementType()) +
           ";";
  }

  private void assertBeforeEventFired(PsiTreeChangeEvent afterEvent) {
    PsiTreeChangeEventImpl.PsiEventType code = ((PsiTreeChangeEventImpl)afterEvent).getCode();
    assertFalse(code.name(), code.name().startsWith("BEFORE_"));
    PsiTreeChangeEventImpl.PsiEventType beforeCode = PsiTreeChangeEventImpl.PsiEventType.values()[code.ordinal() - 1];
    assertTrue(beforeCode.name(), beforeCode.name().startsWith("BEFORE_"));
    String beforeText = eventText(afterEvent, beforeCode);
    int i = eventsFired.indexOf(beforeText);
    assertTrue("Event '" + beforeText + "' must be fired. Events so far: " + eventsFired, i >= 0);
  }
  private void doTestEvents(String newText) {
    try {
      getPsiManager().addPsiTreeChangeListener(listener);
      eventsFired = "";
      original = getFile().getText();
      Document document = PsiDocumentManager.getInstance(getProject()).getDocument(getFile());
      ApplicationManager.getApplication().runWriteAction(() -> document.setText(newText));

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

      ApplicationManager.getApplication().runWriteAction(() -> document.setText(original));

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
    finally {
      getPsiManager().removePsiTreeChangeListener(listener);
    }
  }

  public void testCopyFile() throws Exception {
    VirtualFile original = createFile(myModule, mySrcDir1, "a.xml", "<tag/>").getVirtualFile();

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiDirectory psiDir2 = PsiManager.getInstance(myProject).findDirectory(mySrcDir2);
    assertNotNull(psiDir2);
    WriteAction.run(() -> original.copy(this, mySrcDir2, "b.xml"));

    assertEquals("""
                   beforeChildAddition
                   childAdded
                   """, listener.getEventsString());
  }

  public void testSuccessfulRecoveryAfterTreeChangePreprocessorThrowsException() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      PsiFile psiFile = createFile("a.xml", "<tag/>");
      VirtualFile vFile = psiFile.getVirtualFile();
      Document document = FileDocumentManager.getInstance().getDocument(vFile);
      assert document != null;

      PsiTreeChangePreprocessor preprocessor = event -> {
        if (!event.getCode().name().startsWith("BEFORE") && !event.isGenericChange()) {
          throw new NullPointerException();
        }
      };

      Disposable disposable = Disposer.newDisposable();
      ((PsiManagerEx)getPsiManager()).addTreeChangePreprocessor(preprocessor, disposable);
      try {
        WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, " "));
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        fail("NPE expected");
      }
      catch (AssertionError e) {
        assertInstanceOf(e.getCause(), NullPointerException.class);
      }
      finally {
        Disposer.dispose(disposable);
      }

      WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, " "));
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      assertEquals("  <tag/>", getPsiManager().findFile(vFile).getText());
    });
  }
}
