package com.intellij.psi.impl.file.impl;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.WaitFor;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class PsiEventsTest extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.impl.PsiEventsTest");

  private VirtualFile myPrjDir1;
  private VirtualFile myPrjDir2;
  private VirtualFile myPrjDir3;
  private VirtualFile mySrcDir1;
  private VirtualFile mySrcDir2;
  private VirtualFile mySrcDir3;
  private VirtualFile mySrcDir4;
  private VirtualFile myClsDir1;
  private VirtualFile myExcludedDir1;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = FileUtil.createTempFile(getName(), "");
    root.delete();
    root.mkdir();
    myFilesToDelete.add(root);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);

          myPrjDir1 = rootVFile.createChildDirectory(null, "prj1");
          mySrcDir1 = myPrjDir1.createChildDirectory(null, "src1");
          mySrcDir2 = myPrjDir1.createChildDirectory(null, "src2");

          myPrjDir2 = rootVFile.createChildDirectory(null, "prj2");
          mySrcDir3 = myPrjDir2;


          myClsDir1 = myPrjDir1.createChildDirectory(null, "cls1");

          myExcludedDir1 = mySrcDir1.createChildDirectory(null, "excluded");

          PsiTestUtil.addContentRoot(myModule, myPrjDir1);
          PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
          PsiTestUtil.addSourceRoot(myModule, mySrcDir2);
          PsiTestUtil.addContentRoot(myModule, myPrjDir2);
          PsiTestUtil.addExcludedRoot(myModule, myExcludedDir1);
          ModuleRootModificationUtil.addModuleLibrary(myModule, myClsDir1.getUrl());
          PsiTestUtil.addSourceRoot(myModule, mySrcDir3);
        } catch (IOException e) {
          LOG.error(e);
        }
      }
    });

    //((PsiManagerImpl)myPsiManager).getFileManager().disbleVFSEventsProcessing();
  }

  public void testCreateFile() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiDirectory psiDir = fileManager.findDirectory(myPrjDir1);
    myPrjDir1.createChildData(null, "a.txt");

    String string = listener.getEventsString();
    String expected =
            "beforeChildAddition\n" +
            "childAdded\n";
    assertEquals(expected, string);
  }

  public void testCreateDirectory() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiDirectory psiDir = fileManager.findDirectory(myPrjDir1);
    myPrjDir1.createChildDirectory(null, "aaa");

    String string = listener.getEventsString();
    String expected =
            "beforeChildAddition\n" +
            "childAdded\n";
    assertEquals(expected, string);
  }

  public void testDeleteFile() throws Exception {
    VirtualFile file = myPrjDir1.createChildData(null, "a.txt");

    FileManager fileManager = myPsiManager.getFileManager();
    PsiFile psiFile = fileManager.findFile(file);//it's important to hold the reference

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.delete(null);

    String string = listener.getEventsString();
    String expected =
            "beforeChildRemoval\n" +
            "childRemoved\n";
    assertEquals(expected, string);
  }

  public void testDeleteDirectory() throws Exception {
    VirtualFile file = myPrjDir1.createChildDirectory(null, "aaa");

    FileManager fileManager = myPsiManager.getFileManager();
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.delete(null);

    String string = listener.getEventsString();
    String expected =
            "beforeChildRemoval\n" +
            "childRemoved\n";
    assertEquals(expected, string);
  }

  public void testRenameFile1() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildData(null, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.rename(null, "b.txt");

    String string = listener.getEventsString();
    String expected =
            "beforePropertyChange\n" +
            "propertyChanged\n";
    assertEquals(expected, string);
  }

  public void testRenameFile2() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildData(null, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.rename(null, "b.xml");

    String string = listener.getEventsString();
    String expected =
            "beforeChildReplacement\n" +
            "childReplaced\n";
    assertEquals(expected, string);
  }

  public void testRenameFile3() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildData(null, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.rename(null, "CVS");

    String string = listener.getEventsString();
    String expected =
            "beforeChildRemoval\n" +
            "childRemoved\n";
    assertEquals(expected, string);
    assertNull(fileManager.findFile(file));
  }

  public void testRenameFile4() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildData(null, "CVS");
    PsiDirectory psiDirectory = fileManager.findDirectory(file.getParent());

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.rename(null, "aaa.txt");

    String string = listener.getEventsString();
    String expected =
            "beforeChildAddition\n" +
            "childAdded\n";
    assertEquals(expected, string);
  }

  public void testRenameDirectory1() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildDirectory(null, "dir1");
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.rename(null, "dir2");

    String string = listener.getEventsString();
    String expected =
            "beforePropertyChange\n" +
            "propertyChanged\n";
    assertEquals(expected, string);
  }

  public void testRenameDirectory2() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildDirectory(null, "dir1");
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.rename(null, "CVS");

    String string = listener.getEventsString();
    String expected =
            "beforeChildRemoval\n" +
            "childRemoved\n";
    assertEquals(expected, string);
    assertNull(fileManager.findDirectory(file));
  }

  public void testRenameDirectory3() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildDirectory(null, "CVS");
    PsiDirectory psiDirectory = fileManager.findDirectory(file.getParent());

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.rename(null, "dir");

    String string = listener.getEventsString();
    String expected =
            "beforeChildAddition\n" +
            "childAdded\n";
    assertEquals(expected, string);
  }

  public void testMakeFileReadOnly() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildData(null, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    final EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    ReadOnlyAttributeUtil.setReadOnlyAttribute(file, true);

    String string = listener.getEventsString();
    final String expected =
            "beforePropertyChange\n" +
            "propertyChanged\n";

    new WaitFor(500){
      @Override
      protected boolean condition() {
        return expected.equals(listener.getEventsString());
      }
    }.assertCompleted(expected);

    ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false);
  }

  public void testMoveFile1() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildData(null, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.move(null, myPrjDir1.getParent());

    String string = listener.getEventsString();
    String expected =
            "beforeChildMovement\n" +
            "childMoved\n";
    assertEquals(expected, string);
  }

  public void testMoveFile2() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildData(null, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.move(null, myExcludedDir1);

    String string = listener.getEventsString();
    String expected =
            "beforeChildRemoval\n" +
            "childRemoved\n";
    assertEquals(expected, string);
    assertNull(fileManager.findFile(file));
  }

  public void testMoveFile3() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myExcludedDir1.createChildData(null, "a.txt");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.move(null, myPrjDir1);

    String string = listener.getEventsString();
    String expected =
            "beforeChildAddition\n" +
            "childAdded\n";
    assertEquals(expected, string);
  }

  public void testMoveFile4() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myExcludedDir1.createChildData(null, "a.txt");
    VirtualFile subdir = myExcludedDir1.createChildDirectory(null, "subdir");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.move(null, subdir);

    String string = listener.getEventsString();
    String expected = "";
    assertEquals(expected, string);
  }

  public void testMoveDirectory1() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildDirectory(null, "dir");
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.move(null, myPrjDir1.getParent());

    String string = listener.getEventsString();
    String expected =
            "beforeChildMovement\n" +
            "childMoved\n";
    assertEquals(expected, string);
  }

  public void testMoveDirectory2() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildDirectory(null, "dir");
    PsiDirectory psiDirectory = fileManager.findDirectory(file);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.move(null, myExcludedDir1);

    String string = listener.getEventsString();
    String expected =
            "beforeChildRemoval\n" +
            "childRemoved\n";
    assertEquals(expected, string);
    assertNull(fileManager.findDirectory(file));
  }

  public void testMoveDirectory3() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myExcludedDir1.createChildDirectory(null, "dir");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.move(null, myPrjDir1);

    String string = listener.getEventsString();
    String expected =
            "beforeChildAddition\n" +
            "childAdded\n";
    assertEquals(expected, string);
  }

  public void testMoveDirectory4() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myExcludedDir1.createChildDirectory(null, "dir");
    VirtualFile subdir = myExcludedDir1.createChildDirectory(null, "subdir");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    file.move(null, subdir);

    String string = listener.getEventsString();
    String expected = "";
    assertEquals(expected, string);
  }

  public void testChangeFile() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = myPrjDir1.createChildData(null, "a.txt");
    VfsUtil.saveText(file, "aaa");
    PsiFile psiFile = fileManager.findFile(file);
    psiFile.getText();

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    VfsUtil.saveText(file, "bbb");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    /*
    assertEquals("", listener.getEventsString());

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    */

    assertEquals(
            "beforeChildrenChange\n" +
            "beforeChildReplacement\n" +
            "childReplaced\n"+
            "childrenChanged\n",
            listener.getEventsString());
  }

  public void testAddExcludeRoot() throws IOException {
    final VirtualFile dir = myPrjDir1.createChildDirectory(null, "aaa");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiTestUtil.addExcludedRoot(myModule, dir);


    String string = listener.getEventsString();
    String expected =
            "beforePropertyChange\n" +
            "propertyChanged\n";
    assertEquals(expected, string);
  }

  public void testAddSourceRoot() throws IOException {
    final VirtualFile dir = myPrjDir1.createChildDirectory(null, "aaa");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiTestUtil.addSourceRoot(myModule, dir);

    String string = listener.getEventsString();
    String expected =
            "beforePropertyChange\n" +
            "propertyChanged\n";
    assertEquals(expected, string);
  }

  public void testModifyFileTypes() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ((FileTypeManagerEx)FileTypeManager.getInstance()).fireBeforeFileTypesChanged();
        ((FileTypeManagerEx)FileTypeManager.getInstance()).fireFileTypesChanged();
      }
    });


    String string = listener.getEventsString();
    String expected =
      "beforePropertyChange\n" +
      "propertyChanged\n";
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
    virtualFile.rename(this, "b.xml");
  }

  String newText;
  String original;
  String eventsFired = "";
  PsiTreeChangeListener listener;
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
      this.newText = newText;
      original = getFile().getText();
      Document document = PsiDocumentManager.getInstance(getProject()).getDocument(getFile());
      document.setText(newText);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

      document.setText(original);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
    finally {
      getPsiManager().removePsiTreeChangeListener(listener);
    }
  }
}
