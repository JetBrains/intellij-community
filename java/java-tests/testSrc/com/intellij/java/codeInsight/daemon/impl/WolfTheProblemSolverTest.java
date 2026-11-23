// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.MockWolfTheProblemSolver;
import com.intellij.codeInsight.daemon.impl.WolfTheProblemSolverImpl;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class WolfTheProblemSolverTest {
  private static final Logger LOG = Logger.getInstance(WolfTheProblemSolverTest.class);
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/projectWide";
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());
  private MockWolfTheProblemSolver myWolfTheProblemSolver;

  @BeforeEach
  void setUp() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    myWolfTheProblemSolver = prepareWolf(fixture.getProject(), fixture.getTestRootDisposable());
  }

  @Test
  public void testHighlighting() {
    VirtualFile root = configureRoot();
    VirtualFile x = root.findFileByRelativePath("x/X.java");
    VirtualFile y = root.findFileByRelativePath("y/Y.java");
    assertThat(highlightErrors(x)).isNotEmpty();
    assertThat(highlightErrors(y)).isNotEmpty();

    assertTrue(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));

    deleteMethodWithProblem(x);
    assertTrue(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));
    assertThat(highlightErrors(x)).isEmpty();
    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));

    deleteMethodWithProblem(y);
    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));
    assertThat(highlightErrors(y)).isEmpty();
    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
    assertFalse(myWolfTheProblemSolver.isProblemFile(y));
  }

  private void deleteMethodWithProblem(VirtualFile virtualFile) {
    Project project = daemonFixture.get().getProject();
    final PsiClass xClass = ReadAction.computeBlocking(() -> ((PsiJavaFile)PsiManager.getInstance(project).findFile(virtualFile)).getClasses()[0]);
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(project, () -> WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        xClass.getMethods()[0].delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), null, null));
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
  }

  @Test
  public void testRemoveFile() {
    VirtualFile root = configureRoot();
    VirtualFile x = root.findFileByRelativePath("x/X.java");
    VirtualFile y = root.findFileByRelativePath("y/Y.java");
    assertThat(highlightErrors(x)).isNotEmpty();
    assertThat(highlightErrors(y)).isNotEmpty();

    assertTrue(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));
    PsiManager psiManager = PsiManager.getInstance(daemonFixture.get().getProject());
    final PsiJavaFile psiX = ReadAction.computeBlocking(() -> (PsiJavaFile)psiManager.findFile(x));
    PsiJavaFile psiY = ReadAction.computeBlocking(() -> (PsiJavaFile)psiManager.findFile(y));
    assertNotNull(psiY);

    WriteCommandAction.runWriteCommandAction(null, psiX::delete);
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
    assertFalse(myWolfTheProblemSolver.isProblemFile(x));
    assertTrue(myWolfTheProblemSolver.isProblemFile(y));
  }

  @Test
  public void testExcludedFile() {
    VirtualFile root = configureRoot();
    VirtualFile x = root.findFileByRelativePath("x/X.java");
    assertThat(highlightErrors(x)).isNotEmpty();
    assertTrue(myWolfTheProblemSolver.isProblemFile(x));

    DaemonAnalyzerFixture fixture = daemonFixture.get();
    ExcludeEntryDescription description = new ExcludeEntryDescription(x, false, true, fixture.getTestRootDisposable());
    CompilerConfiguration.getInstance(fixture.getProject()).getExcludedEntriesConfiguration().addExcludeEntryDescription(description);
    FileStatusManager.getInstance(fixture.getProject()).fileStatusesChanged();
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
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

  private VirtualFile configureRoot() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + "/x/X.java", BASE_PATH);
    return ModuleRootManager.getInstance(fixture.getModule()).getContentRoots()[0];
  }

  @Test
  public void testEvents() {
    VirtualFile root = configureRoot();
    MyProblemListener handler = new MyProblemListener();
    daemonFixture.get().getProject().getMessageBus().connect(daemonFixture.get().getTestRootDisposable()).subscribe(ProblemListener.TOPIC, handler);

    VirtualFile x = root.findFileByRelativePath("x/X.java");
    VirtualFile y = root.findFileByRelativePath("y/Y.java");
    assertThat(highlightErrors(x)).isNotEmpty();
    handler.verifyEvents(Collections.singleton(x), emptySet(), emptySet());
    assertTrue(myWolfTheProblemSolver.hasSyntaxErrors(x));

    assertThat(highlightErrors(y)).isNotEmpty();
    handler.verifyEvents(Collections.singleton(y), emptySet(), emptySet());
    assertFalse(myWolfTheProblemSolver.hasSyntaxErrors(y));

    Project project = daemonFixture.get().getProject();
    PsiManager psiManager = PsiManager.getInstance(project);
    final PsiJavaFile psiX = ReadAction.computeBlocking(() -> (PsiJavaFile)psiManager.findFile(x));
    WriteCommandAction.runWriteCommandAction(null, () -> {
              psiX.getClasses()[0].replace(psiX.getClasses()[0]);

          });
    highlightErrors(x);
    handler.verifyEvents(emptySet(), Collections.singleton(x), emptySet());
    assertFalse(myWolfTheProblemSolver.hasSyntaxErrors(y));

    PsiJavaFile psiY = ReadAction.computeBlocking(() -> (PsiJavaFile)psiManager.findFile(y));
    assertNotNull(psiY);
    final PsiClass newX = ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(project).createClassFromText("",null));
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        psiX.getClasses()[0].replace(newX);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

    }), null, null));
    assertThat(highlightErrors(x)).isEmpty();
    handler.verifyEvents(emptySet(), emptySet(), Collections.singleton(x));
    assertFalse(myWolfTheProblemSolver.hasSyntaxErrors(x));
  }

  @Test
  public void testExternalSources() throws Exception {
    VirtualFile root = configureRoot();
    MyProblemListener handler = new MyProblemListener();
    daemonFixture.get().getProject().getMessageBus().connect(daemonFixture.get().getTestRootDisposable()).subscribe(ProblemListener.TOPIC, handler);

    VirtualFile t = root.findFileByRelativePath("foo.java");
    Object source1 = new Object();
    Object source2 = new Object();
    reportExternalProblem(source1, t);
    handler.verifyEvents(Collections.singleton(t), emptySet(), emptySet());
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
    assertTrue(myWolfTheProblemSolver.isProblemFile(t));
    assertTrue(myWolfTheProblemSolver.hasProblemFilesBeneath(daemonFixture.get().getModule()));

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

  @Test
  public void testRegularAndExternalProblems() throws Exception {
    VirtualFile root = configureRoot();
    MyProblemListener handler = new MyProblemListener();
    daemonFixture.get().getProject().getMessageBus().connect(daemonFixture.get().getTestRootDisposable()).subscribe(ProblemListener.TOPIC, handler);

    Object source1 = new Object();
    VirtualFile x = root.findFileByRelativePath("x/X.java");
    reportExternalProblem(source1, x);
    handler.verifyEvents(Collections.singleton(x), emptySet(), emptySet());

    assertThat(highlightErrors(x)).isNotEmpty();
    handler.verifyEvents(emptySet(), Collections.singleton(x), emptySet());

    deleteMethodWithProblem(x);
    assertThat(highlightErrors(x)).isEmpty();
    handler.verifyEvents(emptySet(), Collections.singleton(x), emptySet());
  }

  private void reportExternalProblem(@NotNull Object source1, @NotNull VirtualFile x) throws ExecutionException, InterruptedException {
    ReadAction.nonBlocking(() -> myWolfTheProblemSolver.reportProblemsFromExternalSource(x, source1)).submit(AppExecutorUtil.getAppExecutorService()).get();
  }

  @NotNull
  private List<HighlightInfo> highlightErrors(@NotNull VirtualFile virtualFile) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(daemonFixture.get().getProject());
    Editor editor = EdtTestUtil.runInEdtAndGet(() -> {
      documentManager.commitAllDocuments();
      FileEditor fileEditor = FileEditorManagerEx.getInstanceEx(daemonFixture.get().getProject()).openFile(virtualFile, false)[0];
      return ((TextEditor)fileEditor).getEditor();
    });
    PsiFile psiFile = ReadAction.computeBlocking(() -> documentManager.getPsiFile(editor.getDocument()));

    List<HighlightInfo> infos = CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, new int[0], false);
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
    return DaemonAnalyzerFixture.filter(infos, HighlightSeverity.ERROR);
  }

  @Test
  public void testChangeInsideBlock() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(fixture.getProject(), () -> {
      fixture.configureByFile(BASE_PATH + "/pack/InsideBlock.java");
    }, null, null));
    final VirtualFile virtualFile = fixture.getFile().getVirtualFile();
    List<HighlightInfo> errors = fixture.highlightErrors();
    myWolfTheProblemSolver.waitForFilesQueuedForInvalidationAreProcessed();
    assertTrue(myWolfTheProblemSolver.isProblemFile(virtualFile));
    assertThat(errors).hasSize(1);
    fixture.type(';');
    errors = fixture.highlightErrors();
    assertThat(errors).isEmpty();
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
