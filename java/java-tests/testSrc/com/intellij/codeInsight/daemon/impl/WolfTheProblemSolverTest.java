// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.ProblemListener;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.emptySet;

public class WolfTheProblemSolverTest extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/projectWide";
  private MockWolfTheProblemSolver myWolfTheProblemSolver;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myWolfTheProblemSolver = prepareWolf(myProject, getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    myWolfTheProblemSolver = null;
    super.tearDown();
  }

  public void testHighlighting() throws Exception {
    VirtualFile root = configureRoot();
    VirtualFile x = root.findFileByRelativePath("x/X.java");
    VirtualFile y = root.findFileByRelativePath("y/Y.java");
    assertNotEmpty(highlightErrors(x));
    assertNotEmpty(highlightErrors(y));

    assertTrue(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));

    deleteMethodWithProblem(x);
    assertTrue(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));
    assertEmpty(highlightErrors(x));
    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));

    deleteMethodWithProblem(y);
    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));
    assertEmpty(highlightErrors(y));
    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
    assertFalse(myWolfTheProblemSolver.isProblemFile(y));
  }

  private void deleteMethodWithProblem(VirtualFile virtualFile) {
    PsiJavaFile psiX = (PsiJavaFile)myPsiManager.findFile(virtualFile);
    final PsiClass xClass = psiX.getClasses()[0];
    CommandProcessor.getInstance().executeCommand(getProject(), () -> WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        xClass.getMethods()[0].delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), null, null);
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
  }

  public void testRemoveFile() throws Exception {
    VirtualFile root = configureRoot();
    VirtualFile x = root.findFileByRelativePath("x/X.java");
    VirtualFile y = root.findFileByRelativePath("y/Y.java");
    assertNotEmpty(highlightErrors(x));
    assertNotEmpty(highlightErrors(y));

    assertTrue(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));
    final PsiJavaFile psiX = (PsiJavaFile)myPsiManager.findFile(x);
    PsiJavaFile psiY = (PsiJavaFile)myPsiManager.findFile(y);
    assertNotNull(psiY);

    WriteCommandAction.runWriteCommandAction(null, psiX::delete);
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));
  }

  public void testExcludedFile() throws Exception {
    VirtualFile root = configureRoot();
    VirtualFile x = root.findFileByRelativePath("x/X.java");
    assertNotEmpty(highlightErrors(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(x));

    ExcludeEntryDescription description = new ExcludeEntryDescription(x, false, true, getTestRootDisposable());
    CompilerConfiguration.getInstance(myProject).getExcludedEntriesConfiguration().addExcludeEntryDescription(description);
    FileStatusManager.getInstance(myProject).fileStatusesChanged();
    UIUtil.dispatchAllInvocationEvents();
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();

    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
    highlightErrors(x);
    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
  }

  @NotNull
  public static MockWolfTheProblemSolver prepareWolf(@NotNull Project project, @NotNull Disposable parentDisposable) {
    MockWolfTheProblemSolver wolfTheProblemSolver = (MockWolfTheProblemSolver)WolfTheProblemSolver.getInstance(project);

    WolfTheProblemSolverImpl theRealSolver = (WolfTheProblemSolverImpl)WolfTheProblemSolverImpl.createTestInstance(project);
    wolfTheProblemSolver.setDelegate(theRealSolver);
    Disposer.register(parentDisposable, theRealSolver);
    return wolfTheProblemSolver;

  }

  private VirtualFile configureRoot() throws Exception {
    configureByFile(BASE_PATH + "/x/X.java", BASE_PATH);
    return ModuleRootManager.getInstance(myModule).getContentRoots()[0];
  }

  public void testEvents() throws Exception {
    VirtualFile root = configureRoot();
    MyProblemListener handler = new MyProblemListener();
    myProject.getMessageBus().connect(getTestRootDisposable()).subscribe(ProblemListener.TOPIC, handler);

    VirtualFile x = root.findFileByRelativePath("x/X.java");
    VirtualFile y = root.findFileByRelativePath("y/Y.java");
    assertNotEmpty(highlightErrors(x));
    handler.verifyEvents(Collections.singleton(x), emptySet(), emptySet());
    assertTrue(myWolfTheProblemSolver.hasSyntaxErrors(x));

    assertNotEmpty(highlightErrors(y));
    handler.verifyEvents(Collections.singleton(y), emptySet(), emptySet());
    assertFalse(myWolfTheProblemSolver.hasSyntaxErrors(y));

    final PsiJavaFile psiX = (PsiJavaFile)myPsiManager.findFile(x);
    WriteCommandAction.runWriteCommandAction(null, () -> {
              psiX.getClasses()[0].replace(psiX.getClasses()[0]);

          });
    highlightErrors(x);
    handler.verifyEvents(emptySet(), Collections.singleton(x), emptySet());
    assertFalse(myWolfTheProblemSolver.hasSyntaxErrors(y));

    PsiJavaFile psiY = (PsiJavaFile)myPsiManager.findFile(y);
    assertNotNull(psiY);
    final PsiClass newX = myJavaFacade.getElementFactory().createClassFromText("",null);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        psiX.getClasses()[0].replace(newX);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

    }), null, null);
    assertEmpty(highlightErrors(x));
    handler.verifyEvents(emptySet(), emptySet(), Collections.singleton(x));
    assertFalse(myWolfTheProblemSolver.hasSyntaxErrors(x));
  }

  public void testExternalSources() throws Exception {
    VirtualFile root = configureRoot();
    MyProblemListener handler = new MyProblemListener();
    myProject.getMessageBus().connect(getTestRootDisposable()).subscribe(ProblemListener.TOPIC, handler);

    VirtualFile t = root.findFileByRelativePath("foo.java");
    Object source1 = new Object();
    Object source2 = new Object();
    reportExternalProblem(source1, t);
    handler.verifyEvents(Collections.singleton(t), emptySet(), emptySet());
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
    assertTrue(myWolfTheProblemSolver.isProblemFile(t));
    assertTrue(myWolfTheProblemSolver.hasProblemFilesBeneath(myModule));

    reportExternalProblem(source2, t);
    handler.verifyEvents(emptySet(), Collections.singleton(t), emptySet());

    clearExternalProblem(t, source2);
    handler.verifyEvents(emptySet(), Collections.singleton(t), emptySet());

    clearExternalProblem(t, source1);
    handler.verifyEvents(emptySet(), emptySet(), Collections.singleton(t));
  }

  private void clearExternalProblem(VirtualFile t, Object source2) throws ExecutionException, InterruptedException {
    ReadAction.nonBlocking(() -> myWolfTheProblemSolver.clearProblemsFromExternalSource(t, source2)).submit(AppExecutorUtil.getAppExecutorService()).get();
  }

  public void testRegularAndExternalProblems() throws Exception {
    VirtualFile root = configureRoot();
    MyProblemListener handler = new MyProblemListener();
    myProject.getMessageBus().connect(getTestRootDisposable()).subscribe(ProblemListener.TOPIC, handler);

    Object source1 = new Object();
    VirtualFile x = root.findFileByRelativePath("x/X.java");
    reportExternalProblem(source1, x);
    handler.verifyEvents(Collections.singleton(x), emptySet(), emptySet());

    assertNotEmpty(highlightErrors(x));
    handler.verifyEvents(emptySet(), Collections.singleton(x), emptySet());

    deleteMethodWithProblem(x);
    assertEmpty(highlightErrors(x));
    handler.verifyEvents(emptySet(), Collections.singleton(x), emptySet());
  }

  private void reportExternalProblem(@NotNull Object source1, @NotNull VirtualFile x) throws ExecutionException, InterruptedException {
    ReadAction.nonBlocking(() -> myWolfTheProblemSolver.reportProblemsFromExternalSource(x, source1)).submit(AppExecutorUtil.getAppExecutorService()).get();
  }

  @NotNull
  private List<HighlightInfo> highlightErrors(@NotNull VirtualFile virtualFile) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    documentManager.commitAllDocuments();
    FileEditor fileEditor = FileEditorManagerEx.getInstanceEx(getProject()).openFile(virtualFile, false)[0];
    Editor editor = ((TextEditor)fileEditor).getEditor();
    PsiFile psiFile = documentManager.getPsiFile(editor.getDocument());

    List<HighlightInfo> infos = CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, new int[0], false);
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
    return filter(infos, HighlightSeverity.ERROR);
  }

  public void testChangeInsideBlock() {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      try {
        configureByFile(BASE_PATH + "/pack/InsideBlock.java");
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }, null, null);
    final VirtualFile virtualFile = myFile.getVirtualFile();
    List<HighlightInfo> errors = highlightErrors();
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
    assertTrue(myWolfTheProblemSolver.isProblemFile(virtualFile));
    assertSize(1, errors);
    type(';');
    errors = highlightErrors();
    assertEmpty(errors);
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
    assertFalse(myWolfTheProblemSolver.isProblemFile(virtualFile));
  }

  private static final class MyProblemListener implements ProblemListener {
    private final Set<VirtualFile> myEventAdded;
    private final Set<VirtualFile> myEventChanged;
    private final Set<VirtualFile> myEventRemoved;

    private MyProblemListener() {
      myEventAdded = new HashSet<>();
      myEventChanged = new HashSet<>();
      myEventRemoved = new HashSet<>();
    }

    @Override
    public void problemsAppeared(@NotNull VirtualFile file) {
      myEventAdded.add(file);
    }

    @Override
    public void problemsChanged(@NotNull VirtualFile file) {
      myEventChanged.add(file);
    }

    @Override
    public void problemsDisappeared(@NotNull VirtualFile file) {
      myEventRemoved.add(file);
    }

    void verifyEvents(Collection<VirtualFile> added, Collection<VirtualFile> changed, Collection<VirtualFile> removed) {
      assertEquals(added, myEventAdded);
      assertEquals(changed, myEventChanged);
      assertEquals(removed, myEventRemoved);
      myEventAdded.clear();
      myEventChanged.clear();
      myEventRemoved.clear();
    }
  }
}
