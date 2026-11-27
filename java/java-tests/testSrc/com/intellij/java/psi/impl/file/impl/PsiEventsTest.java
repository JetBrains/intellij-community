// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.file.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.file.impl.FileManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.WaitFor;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildData;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.move;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.rename;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.setFileText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"ConstantConditions", "FieldCanBeLocal", "unused"})
@SkipSlowTestLocally
@TestApplication
public class PsiEventsTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private Project myProject;
  private Module myModule;
  private PsiManagerEx myPsiManager;
  private Disposable myTestRootDisposable;
  private VirtualFile myPrjDir1;
  private VirtualFile myPrjDir2;
  private VirtualFile mySrcDir1;
  private VirtualFile mySrcDir2;
  private VirtualFile mySrcDir3;
  private VirtualFile myClsDir1;
  private VirtualFile myIgnoredDir;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    myProject = fixture.getProject();
    myModule = fixture.getModule();
    myPsiManager = fixture.getPsiManager();
    myTestRootDisposable = fixture.getTestRootDisposable();
    VirtualFile rootVFile = fixture.createTempVirtualDir();

    WriteAction.runAndWait(() -> {

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

  @Test
  public void testCreateFile() {
    FileManager fileManager = myPsiManager.getFileManager();
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    PsiDirectory psiDir = ReadAction.computeBlocking(() -> fileManager.findDirectory(myPrjDir1));
    createChildData(myPrjDir1, "a.txt");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(expected, string, psiDir.getName());
  }

  @Test
  public void testCreateDirectory() {
    FileManager fileManager = myPsiManager.getFileManager();
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    PsiDirectory psiDir = ReadAction.computeBlocking(() -> fileManager.findDirectory(myPrjDir1));
    createChildDirectory(myPrjDir1, "aaa");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(expected, string, psiDir.getName());
  }

  @Test
  public void testDeleteFile() {
    VirtualFile file = createChildData(myPrjDir1, "a.txt");

    FileManager fileManager = myPsiManager.getFileManager();
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file));//it's important to hold the reference

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    VfsTestUtil.deleteFile(file);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(expected, string, psiFile.getName());
  }

  @Test
  public void testConsequentDeleteDeleteFile() {
    VirtualFile file1 = createChildData(myPrjDir1, "a.txt");
    VirtualFile file2 = createChildData(myPrjDir2, "b.txt");

    FileManager fileManager = myPsiManager.getFileManager();
    PsiFile psiFile1 = ReadAction.computeBlocking(() -> fileManager.findFile(file1));//it's important to hold the reference
    PsiFile psiFile2 = ReadAction.computeBlocking(() -> fileManager.findFile(file2));//it's important to hold the reference

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    VfsTestUtil.deleteFile(file1);
    VfsTestUtil.deleteFile(file2);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(expected, string);
  }

  @Test
  public void testConsequentDeleteDirDeleteFile() {
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    VirtualFile dir = createChildDirectory(myPrjDir2, "dir");

    FileManager fileManager = myPsiManager.getFileManager();
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file)); //it's important to hold the reference
    PsiDirectory psiDir = ReadAction.computeBlocking(() -> fileManager.findDirectory(dir)); //it's important to hold the reference

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    VfsTestUtil.deleteFile(dir);
    VfsTestUtil.deleteFile(file);

    String string = listener.getEventsString();
    String expectedCorrect = """
      beforeChildRemoval
      childRemoved
      beforeChildRemoval
      childRemoved
      """;

    // TODO is the correct result, see IJPL-241524,
    // assertEquals(expectedCorrect, string);

    String expectedDefacto = """
      beforeChildRemoval
      childRemoved
      beforePropertyChange propUnloadedPsi
      propertyChanged propUnloadedPsi
      """;

    // TODO is incorrect result, see IJPL-241524,
    assertEquals(expectedDefacto, string);
  }

  @Test
  public void testConsequentMoveDeleteFile() {
    VirtualFile file1 = createChildData(myPrjDir1, "a.txt");
    VirtualFile file2 = createChildData(myPrjDir2, "b.txt");

    FileManager fileManager = myPsiManager.getFileManager();
    PsiFile psiFile1 = ReadAction.computeBlocking(() -> fileManager.findFile(file1)); //it's important to hold the reference
    PsiFile psiFile2 = ReadAction.computeBlocking(() -> fileManager.findFile(file2)); //it's important to hold the reference

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    move(file1, mySrcDir3);
    VfsTestUtil.deleteFile(file2);

    String string = listener.getEventsString();
    String expected = """
      beforeChildMovement
      childMoved
      beforeChildRemoval
      childRemoved
      """;
    assertEquals(expected, string);
  }

  @Test
  public void testDeleteDirectory() {
    VirtualFile file = createChildDirectory(myPrjDir1, "aaa");

    FileManager fileManager = myPsiManager.getFileManager();
    PsiDirectory psiDirectory = ReadAction.computeBlocking(() -> fileManager.findDirectory(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    VfsTestUtil.deleteFile(file);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(expected, string, psiDirectory.getName());
  }

  @Test
  public void testRenameFile() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file));

    PsiDirectory directory = ReadAction.computeBlocking(() -> fileManager.findDirectory(myPrjDir1));
    assertNotNull(directory);

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    rename(file, "b.txt");

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange fileName
        propertyChanged fileName
        """;
    assertEquals(expected, string, psiFile.getName());
  }

  @Test
  public void testRenameFileWithoutDir() {
    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file));

    GCWatcher.tracking(ReadAction.computeBlocking(() -> fileManager.getCachedDirectory(myPrjDir1))).ensureCollected();

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    rename(file, "b.txt");

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange fileName
        propertyChanged fileName
        """;
    assertEquals(expected, string, psiFile.getName());
  }

  @Test
  public void testRenameFileChangingExtension() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    rename(file, "b.xml");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildReplacement
        childReplaced
        """;
    assertEquals(expected, string, psiFile.getName());
  }

  @Test
  public void testRenameFileToIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    rename(file, "CVS");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(expected, string, psiFile.getName());
    assertNull(ReadAction.computeBlocking(() -> fileManager.findFile(file)));
  }

  @Test
  public void testRenameFileFromIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "CVS");
    PsiDirectory psiDirectory = ReadAction.computeBlocking(() -> fileManager.findDirectory(file.getParent()));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    rename(file, "aaa.txt");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(expected, string, psiDirectory.getName());
  }

  @Test
  public void testRenameDirectory_WithPsiDir() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir1");
    PsiDirectory psiDirectory = ReadAction.computeBlocking(() -> fileManager.findDirectory(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    rename(file, "dir2");

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange directoryName
        propertyChanged directoryName
        """;
    assertEquals(expected, string, psiDirectory.getName());
  }

  @Test
  public void testRenameDirectory_WithoutPsiDir() {
    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir1");

    GCWatcher.tracking(ReadAction.computeBlocking(() -> fileManager.getCachedDirectory(file))).ensureCollected();

    assertNull(ReadAction.computeBlocking(() -> fileManager.getCachedDirectory(file)));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    rename(file, "dir2");

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange propUnloadedPsi
        propertyChanged propUnloadedPsi
        """;
    assertEquals(expected, string, ReadAction.computeBlocking(() -> fileManager.findDirectory(file).getName()));
  }

  @Test
  public void testRenameDirectoryToIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir1");
    PsiDirectory psiDirectory = ReadAction.computeBlocking(() -> fileManager.findDirectory(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    rename(file, "CVS");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(expected, string, psiDirectory.getName());
    assertNull(ReadAction.computeBlocking(() -> fileManager.findDirectory(file)));
  }

  @Test
  public void testRenameDirectoryFromIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "CVS");
    PsiDirectory psiDirectory = ReadAction.computeBlocking(() -> fileManager.findDirectory(file.getParent()));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    rename(file, "dir");

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(expected, string, psiDirectory.getName());
  }

  @Test
  public void testMakeFileReadOnly() throws Exception {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file));

    final EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    WriteAction.runAndWait(() -> ReadOnlyAttributeUtil.setReadOnlyAttribute(file, true));


    final String expected =
      """
        beforePropertyChange writable
        propertyChanged writable
        """;

    new WaitFor(500) {
      @Override
      protected boolean condition() {
        return expected.equals(listener.getEventsString());
      }
    }.assertCompleted(listener.getEventsString());

    WriteAction.runAndWait(() -> ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false));
  }

  @Test
  public void testMoveFile() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    move(file, myPrjDir1.getParent());

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildMovement
        childMoved
        """;
    assertEquals(expected, string, psiFile.getName());
  }

  @Test
  public void testMoveFileToIgnoredDir() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    move(file, myIgnoredDir);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(expected, string, psiFile.getName());
    assertNull(ReadAction.computeBlocking(() -> fileManager.findFile(file)));
  }

  @Test
  public void testMoveFileFromIgnoredDir() {
    VirtualFile file = createChildData(myIgnoredDir, "a.txt");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    move(file, myPrjDir1);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(expected, string);
  }

  @Test
  public void testMoveFileInsideIgnoredDir() {
    VirtualFile file = createChildData(myIgnoredDir, "a.txt");
    VirtualFile subdir = createChildDirectory(myIgnoredDir, "subdir");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    move(file, subdir);

    String string = listener.getEventsString();
    String expected = "";
    assertEquals(expected, string);
  }

  @Test
  public void testMoveDirectory() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir");
    PsiDirectory psiDirectory = ReadAction.computeBlocking(() -> fileManager.findDirectory(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    move(file, myPrjDir1.getParent());

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildMovement
        childMoved
        """;
    assertEquals(expected, string, psiDirectory.getName());
  }

  @Test
  public void testMoveDirectoryToIgnored() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildDirectory(myPrjDir1, "dir");
    PsiDirectory psiDirectory = ReadAction.computeBlocking(() -> fileManager.findDirectory(file));

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    move(file, myIgnoredDir);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildRemoval
        childRemoved
        """;
    assertEquals(expected, string, psiDirectory.getName());
    assertNull(ReadAction.computeBlocking(() -> fileManager.findDirectory(file)));
  }

  @Test
  public void testMoveDirectoryFromIgnored() {
    VirtualFile file = createChildDirectory(myIgnoredDir, "dir");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    move(file, myPrjDir1);

    String string = listener.getEventsString();
    String expected =
      """
        beforeChildAddition
        childAdded
        """;
    assertEquals(expected, string);
  }

  @Test
  public void testMoveDirectoryInsideIgnored() {
    VirtualFile file = createChildDirectory(myIgnoredDir, "dir");
    VirtualFile subdir = createChildDirectory(myIgnoredDir, "subdir");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    move(file, subdir);

    String string = listener.getEventsString();
    String expected = "";
    assertEquals(expected, string);
  }

  @Test
  public void testChangeFile() {
    FileManager fileManager = myPsiManager.getFileManager();
    VirtualFile file = createChildData(myPrjDir1, "a.txt");
    setFileText(file, "aaa");
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());
    PsiFile psiFile = ReadAction.computeBlocking(() -> fileManager.findFile(file));
    assertNotNull(ReadAction.computeBlocking(() -> psiFile.getText())); // Trigger PSI loading

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    setFileText(file, "bbb");
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());

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

  @Test
  public void testAddExcludeRoot() {
    final VirtualFile dir = createChildDirectory(myPrjDir1, "aaa");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    PsiTestUtil.addExcludedRoot(myModule, dir);


    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange roots
        propertyChanged roots
        """;
    assertEquals(expected, string);
  }

  @Test
  public void testAddSourceRoot() {
    final VirtualFile dir = createChildDirectory(myPrjDir1, "aaa");

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    PsiTestUtil.addSourceRoot(myModule, dir);

    String string = listener.getEventsString();
    String expected =
      """
        beforePropertyChange roots
        propertyChanged roots
        """;
    assertEquals(expected, string);
  }

  @Test
  public void testModifyFileTypes() {
    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    WriteAction.runAndWait(() -> {
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

  @Test
  public void testCyclicDispatching() throws Throwable {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final VirtualFile virtualFile = fixture.createFile("a.xml", "<tag/>").getVirtualFile();
    final PsiTreeChangeAdapter listener = new PsiTreeChangeAdapter() {
      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        fixture.getJavaFacade().findClass("XXX", GlobalSearchScope.allScope(myProject));
      }
    };
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);
    rename(virtualFile, "b.xml");
  }

  private PsiFile myFile;
  private String original;
  private String eventsFired = "";
  private PsiTreeChangeListener listener;

  @Test
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

    myFile = heavyFixture.get().createFile("A.java", "class A { int i; }");
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
    assertFalse(code.name().startsWith("BEFORE_"), code.name());
    PsiTreeChangeEventImpl.PsiEventType beforeCode = PsiTreeChangeEventImpl.PsiEventType.values()[code.ordinal() - 1];
    assertTrue(beforeCode.name().startsWith("BEFORE_"), beforeCode.name());
    String beforeText = eventText(afterEvent, beforeCode);
    int i = eventsFired.indexOf(beforeText);
    assertTrue(i >= 0, "Event '" + beforeText + "' must be fired. Events so far: " + eventsFired);
  }

  private void doTestEvents(String newText) {
    Disposable disposable = Disposer.newDisposable();
    try {
      myPsiManager.addPsiTreeChangeListener(listener, disposable);
      eventsFired = "";
      original = ReadAction.computeBlocking(() -> myFile.getText());
      Document document = ReadAction.computeBlocking(() -> PsiDocumentManager.getInstance(myProject).getDocument(myFile));
      WriteAction.runAndWait(() -> document.setText(newText));

      EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());

      WriteAction.runAndWait(() -> document.setText(original));

      EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  @Test
  public void testCopyFile() throws Exception {
    VirtualFile original = heavyFixture.get().createFile(myModule, mySrcDir1, "a.xml", "<tag/>").getVirtualFile();

    EventsTestListener listener = new EventsTestListener();
    myPsiManager.addPsiTreeChangeListener(listener, myTestRootDisposable);

    PsiDirectory psiDir2 = ReadAction.computeBlocking(() -> PsiManager.getInstance(myProject).findDirectory(mySrcDir2));
    assertNotNull(psiDir2);
    WriteAction.runAndWait(() -> original.copy(this, mySrcDir2, "b.xml"));

    assertEquals("""
                   beforeChildAddition
                   childAdded
                   """, listener.getEventsString());
  }

  @Test
  public void testSuccessfulRecoveryAfterTreeChangePreprocessorThrowsException() throws Exception {
    PsiFile psiFile = heavyFixture.get().createFile("a.xml", "<tag/>");
    VirtualFile vFile = psiFile.getVirtualFile();
    Document document = ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(vFile));
    assert document != null;

    PsiTreeChangePreprocessor preprocessor = event -> {
      if (!event.getCode().name().startsWith("BEFORE") && !event.isGenericChange()) {
        throw new NullPointerException();
      }
    };

    Disposable disposable = Disposer.newDisposable();
    myPsiManager.addTreeChangePreprocessor(preprocessor, disposable);
    try {
      Throwable error = LoggedErrorProcessor.executeAndReturnLoggedError(() -> {
        WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, " "));
        EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());
      });
      assertInstanceOf(PluginException.class, error);
      assertInstanceOf(NullPointerException.class, error.getCause());
    }
    finally {
      Disposer.dispose(disposable);
    }

    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, " "));
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());
    assertEquals("  <tag/>", ReadAction.computeBlocking(() -> myPsiManager.findFile(vFile).getText()));
  }
}
