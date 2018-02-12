/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.impl.file.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.*;
import com.intellij.util.MemoryDumpHelper;
import com.intellij.util.WaitFor;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

@SkipSlowTestLocally
public class PsiEventsTest extends PsiTestCase {
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

    final File root = FileUtil.createTempFile(getName(), "");
    root.delete();
    root.mkdir();
    myFilesToDelete.add(root);

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
  }

  public void testCreateFile() {
    FileManager fileManager = myPsiManager.getFileManager();
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiDirectory psiDir = fileManager.findDirectory(myPrjDir1);
    createChildData(myPrjDir1, "a.txt");

    String string = listener.getEventsString();
    String expected =
            "beforeChildAddition\n" +
            "childAdded\n";
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
            "beforeChildAddition\n" +
            "childAdded\n";
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
            "beforeChildRemoval\n" +
            "childRemoved\n";
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
            "beforeChildRemoval\n" +
            "childRemoved\n";
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
            "beforePropertyChange fileName\n" +
            "propertyChanged fileName\n";
    assertEquals(psiFile.getName(), expected, string);
  }

  public void testRenameFileWithoutDir() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = fileManager.findFile(file);

    PlatformTestUtil.tryGcSoftlyReachableObjects();


    if (((FileManagerImpl)fileManager).getCachedDirectory(myPrjDir1) != null) {
      LeakHunter.checkLeak(LeakHunter.allRoots(), PsiDirectory.class,
                           directory -> directory.getVirtualFile().equals(myPrjDir1));

      String dumpPath = FileUtil.createTempFile(
        new File(System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir"))), "testRenameFileWithoutDir", ".hprof.zip",
                 false, false).getPath();
      MemoryDumpHelper.captureMemoryDumpZipped(dumpPath);
      System.out.println(dumpPath);

      assertNull(((FileManagerImpl)fileManager).getCachedDirectory(myPrjDir1));
      fail("directory just died");
    }

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "b.txt");

    String string = listener.getEventsString();
    String expected =
            "beforePropertyChange fileName\n" +
            "propertyChanged fileName\n";
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
            "beforeChildReplacement\n" +
            "childReplaced\n";
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
            "beforeChildRemoval\n" +
            "childRemoved\n";
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
            "beforeChildAddition\n" +
            "childAdded\n";
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
            "beforePropertyChange directoryName\n" +
            "propertyChanged directoryName\n";
    assertEquals(psiDirectory.getName(), expected, string);
  }

  public void testRenameDirectory_WithoutPsiDir() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir1");

    PlatformTestUtil.tryGcSoftlyReachableObjects();

    assertNull(((FileManagerImpl)fileManager).getCachedDirectory(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    rename(file, "dir2");

    String string = listener.getEventsString();
    String expected =
            "beforePropertyChange propUnloadedPsi\n" +
            "propertyChanged propUnloadedPsi\n";
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
            "beforeChildRemoval\n" +
            "childRemoved\n";
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
            "beforeChildAddition\n" +
            "childAdded\n";
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
            "beforePropertyChange writable\n" +
            "propertyChanged writable\n";

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
            "beforeChildMovement\n" +
            "childMoved\n";
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
            "beforeChildRemoval\n" +
            "childRemoved\n";
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
            "beforeChildAddition\n" +
            "childAdded\n";
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
            "beforeChildMovement\n" +
            "childMoved\n";
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
            "beforeChildRemoval\n" +
            "childRemoved\n";
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
            "beforeChildAddition\n" +
            "childAdded\n";
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
    psiFile.getText();

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    setFileText(file, "bbb");
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

  public void testAddExcludeRoot() {
    final VirtualFile dir = createChildDirectory(myPrjDir1, "aaa");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiTestUtil.addExcludedRoot(myModule, dir);


    String string = listener.getEventsString();
    String expected =
            "beforePropertyChange roots\n" +
            "propertyChanged roots\n";
    assertEquals(expected, string);
  }

  public void testAddSourceRoot() {
    final VirtualFile dir = createChildDirectory(myPrjDir1, "aaa");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiTestUtil.addSourceRoot(myModule, dir);

    String string = listener.getEventsString();
    String expected =
            "beforePropertyChange roots\n" +
            "propertyChanged roots\n";
    assertEquals(expected, string);
  }

  public void testModifyFileTypes() {
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    ApplicationManager.getApplication().runWriteAction(() -> {
      ((FileTypeManagerEx)FileTypeManager.getInstance()).fireBeforeFileTypesChanged();
      ((FileTypeManagerEx)FileTypeManager.getInstance()).fireFileTypesChanged();
    });


    String string = listener.getEventsString();
    String expected =
      "beforePropertyChange propFileTypes\n" +
      "propertyChanged propFileTypes\n";
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

  private String newText;
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
      this.newText = newText;
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

  public void testPsiEventsComeWhenDocumentAlreadyCommitted() throws Exception {
    myFile = createFile("A.java", "class A { int i; }");
    getPsiManager().addPsiTreeChangeListener(new PsiTreeChangeListener() {
      @Override
      public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
        // did not decide whether the doc should be committed at this point
        //checkCommitted(false, event);
      }

      @Override
      public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
        // did not decide whether the doc should be committed at this point
        //checkCommitted(false, event);
      }

      @Override
      public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
        // did not decide whether the doc should be committed at this point
        //checkCommitted(false, event);
      }

      @Override
      public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
        // did not decide whether the doc should be committed at this point
        //checkCommitted(false, event);
      }

      @Override
      public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
        // did not decide whether the doc should be committed at this point
        //checkCommitted(false, event);
      }

      @Override
      public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
        // did not decide whether the doc should be committed at this point
        //checkCommitted(false, event);
      }

      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        checkCommitted(event);
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        checkCommitted(event);
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        checkCommitted(event);
      }

      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        checkCommitted(event);
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        checkCommitted(event);
      }

      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        checkCommitted(event);
      }
    }, getTestRootDisposable());

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(getFile());
    assertTrue(documentManager.isCommitted(document));

    ApplicationManager.getApplication().runWriteAction(() -> document.setText(""));

    documentManager.commitAllDocuments();
    assertTrue(documentManager.isCommitted(document));
  }

  private static void checkCommitted(PsiTreeChangeEvent event) {
    PsiFile file = event.getFile();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    Document document = documentManager.getDocument(file);
    assertTrue(documentManager.isCommitted(document));
  }

  public void testCopyFile() throws Exception {
    VirtualFile original = createFile(myModule, mySrcDir1, "a.xml", "<tag/>").getVirtualFile();

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener,getTestRootDisposable());

    PsiDirectory psiDir2 = PsiManager.getInstance(myProject).findDirectory(mySrcDir2);
    assertNotNull(psiDir2);
    WriteAction.run(() -> original.copy(this, mySrcDir2, "b.xml"));
    
    assertEquals("beforeChildAddition\n" +
                 "childAdded\n", listener.getEventsString());
  }

  public void testSuccessfulRecoveryAfterTreeChangePreprocessorThrowsException() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());

    PsiFile psiFile = createFile("a.xml", "<tag/>");
    VirtualFile vFile = psiFile.getVirtualFile();
    Document document = FileDocumentManager.getInstance().getDocument(vFile);
    assert document != null;

    PsiTreeChangePreprocessor preprocessor = event -> {
      if (!event.getCode().name().startsWith("BEFORE") && !event.isGenericChange()) {
        throw new NullPointerException();
      }
    };
    ((PsiManagerImpl)getPsiManager()).addTreeChangePreprocessor(preprocessor);
    try {
      WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, " "));
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      fail("NPE expected");
    } catch (AssertionError e) {
      assertInstanceOf(e.getCause(), NullPointerException.class);
    } finally {
      ((PsiManagerImpl)getPsiManager()).removeTreeChangePreprocessor(preprocessor);
    }

    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, " "));
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    assertEquals("  <tag/>", getPsiManager().findFile(vFile).getText());
  }
}
