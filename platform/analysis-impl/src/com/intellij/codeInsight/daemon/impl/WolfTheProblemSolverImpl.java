// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.FileViewProviderUtil;
import com.intellij.codeInsight.problems.ProblemImpl;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.ProblemListener;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class WolfTheProblemSolverImpl extends WolfTheProblemSolver implements Disposable {
  private final Map<VirtualFile, ProblemFileInfo> myProblems = new ConcurrentHashMap<>();
  private final Map<VirtualFile, Set<Object>> myProblemsFromExternalSources = new ConcurrentHashMap<>();
  private final Collection<VirtualFile> myCheckingQueue = new HashSet<>(10); // guarded by myCheckingQueue

  private final Project myProject;
  private final WolfListeners myWolfListeners;

  private WolfTheProblemSolverImpl(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    myProject = project;
    myWolfListeners = new WolfListeners(project, this, coroutineScope);
  }

  @Override
  public void dispose() {
  }

  void doRemove(@NotNull VirtualFile problemFile) {
    ProblemFileInfo old = myProblems.remove(problemFile);
    synchronized (myCheckingQueue) {
      myCheckingQueue.remove(problemFile);
    }
    if (old != null) {
      // firing outside lock
      if (hasProblemsFromExternalSources(problemFile)) {
        fireProblemsChanged(problemFile);
      }
      else {
        fireProblemsDisappeared(problemFile);
      }
    }
  }

  private static final class ProblemFileInfo {
    private final Collection<Problem> problems = ConcurrentCollectionFactory.createConcurrentSet();
    private volatile boolean hasSyntaxErrors;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ProblemFileInfo that = (ProblemFileInfo)o;

      return hasSyntaxErrors == that.hasSyntaxErrors && problems.equals(that.problems);
    }

    @Override
    public int hashCode() {
      int result = problems.hashCode();
      result = 31 * result + (hasSyntaxErrors ? 1 : 0);
      return result;
    }
  }

  void clearSyntaxErrorFlag(@NotNull PsiTreeChangeEvent event) {
    PsiFile psiFile = event.getFile();
    if (psiFile == null) return;
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return;
    ProblemFileInfo info = myProblems.get(virtualFile);
    if (info != null) {
      info.hasSyntaxErrors = false;
    }
  }

  public void startCheckingIfVincentSolvedProblemsYet(@NotNull ProgressIndicator progress,
                                                      @NotNull ProgressableTextEditorHighlightingPass pass)
    throws ProcessCanceledException {
    if (!myProject.isOpen()) return;

    List<VirtualFile> files;
    synchronized (myCheckingQueue) {
      files = new ArrayList<>(myCheckingQueue);
    }
    // (rough approx number of PSI elements = file length/2) * (visitor count = 2 usually)
    long progressLimit = files.stream().filter(VirtualFile::isValid).mapToLong(VirtualFile::getLength).sum();
    pass.setProgressLimit(progressLimit);
    for (VirtualFile virtualFile : files) {
      progress.checkCanceled();
      if (virtualFile == null) break;
      if (!virtualFile.isValid() || orderVincentToCleanTheCar(virtualFile, progress)) {
        doRemove(virtualFile);
      }
      if (virtualFile.isValid()) {
        pass.advanceProgress(virtualFile.getLength());
      }
    }
  }

  // returns true if the car has been cleaned
  private boolean orderVincentToCleanTheCar(@NotNull VirtualFile virtualFile, @NotNull ProgressIndicator progressIndicator) throws ProcessCanceledException {
    if (!isToBeHighlighted(virtualFile)) {
      clearProblems(virtualFile);
      return true; // the file is going to be red waved no more
    }
    if (hasSyntaxErrors(virtualFile)) {
      // optimization: it's no use anyway to try to clean the file with syntax errors, only changing the file itself can help
      return false;
    }
    if (myProject.isDisposed()) return false;
    if (willBeHighlightedAnyway(virtualFile)) return false;

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    if (psiFile == null) return false;
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) return false;

    CodeInsightContext context = FileViewProviderUtil.getCodeInsightContext(psiFile);

    AtomicReference<HighlightInfo> error = new AtomicReference<>();
    boolean hasErrorElement = false;
    //noinspection IncorrectCancellationExceptionHandling
    try {
      ProperTextRange visibleRange = new ProperTextRange(0, document.getTextLength());
      HighlightingSessionImpl.getOrCreateHighlightingSession(psiFile, context, (DaemonProgressIndicator)progressIndicator, visibleRange,
                                                             TextRange.EMPTY_RANGE);
      GeneralHighlightingPass pass = new NasueousGeneralHighlightingPass(psiFile, document, visibleRange, error);
      pass.setContext(context);
      pass.collectInformation(progressIndicator);
      hasErrorElement = pass.hasErrorElement();
    }
    catch (ProcessCanceledException e) {
      if (error.get() != null) {
        ProblemImpl problem = new ProblemImpl(virtualFile, error.get(), hasErrorElement);
        reportProblems(virtualFile, Collections.singleton(problem));
      }
      return false;
    }
    clearProblems(virtualFile);
    return true;
  }

  @Override
  public boolean hasSyntaxErrors(@NotNull VirtualFile file) {
    ProblemFileInfo info = myProblems.get(file);
    return info != null && info.hasSyntaxErrors;
  }

  private boolean willBeHighlightedAnyway(@NotNull VirtualFile file) {
    // opened in some editor and hence will be highlighted automatically sometime later
    FileEditor[] selectedEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
    for (FileEditor editor : selectedEditors) {
      if (!(editor instanceof TextEditor textEditor)) continue;
      Document document = textEditor.getEditor().getDocument();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(document);
      if (psiFile == null) continue;
      if (Comparing.equal(file, psiFile.getVirtualFile())) return true;
    }
    return false;
  }

  @Override
  public boolean hasProblemFilesBeneath(@NotNull Condition<? super VirtualFile> condition) {
    if (!myProject.isOpen()) return false;
    return checkProblemFilesInMap(condition, myProblems) ||
           checkProblemFilesInMap(condition, myProblemsFromExternalSources);
  }

  private static boolean checkProblemFilesInMap(@NotNull Condition<? super VirtualFile> condition, @NotNull Map<? extends VirtualFile, ?> map) {
    if (!map.isEmpty()) {
      for (VirtualFile problemFile : map.keySet()) {
        if (problemFile.isValid() && condition.value(problemFile)) return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasProblemFilesBeneath(@NotNull Module scope) {
    return hasProblemFilesBeneath(virtualFile -> ModuleUtilCore.moduleContainsFile(scope, virtualFile, false));
  }

  @Override
  public void queue(@NotNull VirtualFile suspiciousFile) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (!isToBeHighlighted(suspiciousFile)) return;
    doQueue(suspiciousFile);
  }

  private void doQueue(@NotNull VirtualFile suspiciousFile) {
    synchronized (myCheckingQueue) {
      myCheckingQueue.add(suspiciousFile);
    }
  }

  @Override
  public boolean isProblemFile(@NotNull VirtualFile virtualFile) {
    return hasRegularProblems(virtualFile) || hasProblemsFromExternalSources(virtualFile);
  }

  private boolean hasRegularProblems(@NotNull VirtualFile virtualFile) {
    return myProblems.containsKey(virtualFile);
  }

  private boolean hasProblemsFromExternalSources(@NotNull VirtualFile virtualFile) {
    return myProblemsFromExternalSources.containsKey(virtualFile);
  }

  boolean isToBeHighlighted(@NotNull VirtualFile virtualFile) {
    return ReadAction.compute(() -> {
      for (Condition<VirtualFile> filter : FILTER_EP_NAME.getExtensionList(myProject)) {
        ProgressManager.checkCanceled();
        if (filter.value(virtualFile)) {
          return true;
        }
      }

      return false;
    });
  }

  @Override
  public void weHaveGotProblems(@NotNull VirtualFile virtualFile, @NotNull List<? extends Problem> problems) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (problems.isEmpty()) return;
    if (isToBeHighlighted(virtualFile)) {
      weHaveGotNonIgnorableProblems(virtualFile, problems);
    }
  }

  @Override
  public void weHaveGotNonIgnorableProblems(@NotNull VirtualFile virtualFile, @NotNull List<? extends Problem> problems) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (problems.isEmpty()) return;
    ProblemFileInfo storedProblems = myProblems.computeIfAbsent(virtualFile, __ -> new ProblemFileInfo());
    boolean fireListener = storedProblems.problems.isEmpty();
    storedProblems.problems.addAll(problems);
    doQueue(virtualFile);
    if (fireListener) {
      if (hasProblemsFromExternalSources(virtualFile)) {
        fireProblemsChanged(virtualFile);
      }
      else {
        fireProblemsAppeared(virtualFile);
      }
    }
  }

  @RequiresBackgroundThread
  private void fireProblemsAppeared(@NotNull VirtualFile file) {
    myProject.getMessageBus().syncPublisher(ProblemListener.TOPIC).problemsAppeared(file);
  }

  private void fireProblemsChanged(@NotNull VirtualFile virtualFile) {
    myProject.getMessageBus().syncPublisher(ProblemListener.TOPIC).problemsChanged(virtualFile);
  }

  private void fireProblemsDisappeared(@NotNull VirtualFile problemFile) {
    myProject.getMessageBus().syncPublisher(ProblemListener.TOPIC).problemsDisappeared(problemFile);
  }

  @Override
  public void clearProblems(@NotNull VirtualFile virtualFile) {
    doRemove(virtualFile);
  }

  @Override
  public Problem convertToProblem(@NotNull VirtualFile virtualFile,
                                  int line,
                                  int column,
                                  String @NotNull [] message) {
    if (virtualFile.isDirectory() || virtualFile.getFileType().isBinary()) return null;
    HighlightInfo info = ReadAction.compute(() -> {
      TextRange textRange = getTextRange(virtualFile, line, column);
      String description = StringUtil.join(message, "\n");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
    });
    if (info == null) return null;
    return new ProblemImpl(virtualFile, info, false);
  }

  @Override
  public void reportProblems(@NotNull VirtualFile file, @NotNull Collection<? extends Problem> problems) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    if (problems.isEmpty()) {
      clearProblems(file);
      return;
    }
    if (!isToBeHighlighted(file)) return;
    boolean hasProblemsBefore;
    boolean fireChanged;
    ProblemFileInfo newInfo = new ProblemFileInfo();
    for (Problem problem : problems) {
      newInfo.problems.add(problem);
      newInfo.hasSyntaxErrors |= ((ProblemImpl)problem).isSyntaxOnly();
    }
    ProblemFileInfo oldInfo = myProblems.put(file, newInfo);
    hasProblemsBefore = oldInfo != null;
    fireChanged = hasProblemsBefore && !oldInfo.equals(newInfo);

    doQueue(file);
    boolean hasExternal = hasProblemsFromExternalSources(file);
    if (!hasProblemsBefore && !hasExternal) {
      fireProblemsAppeared(file);
    }
    else if (fireChanged || hasExternal) {
      fireProblemsChanged(file);
    }
  }

  @Override
  public void reportProblemsFromExternalSource(@NotNull VirtualFile file, @NotNull Object source) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (!isToBeHighlighted(file)) return;

    Set<Object> problems = myProblemsFromExternalSources.computeIfAbsent(file, __ -> ConcurrentCollectionFactory.createConcurrentSet());
    boolean isNewFileForExternalSource = problems.isEmpty();
    problems.add(source);

    if (isNewFileForExternalSource && !hasRegularProblems(file)) {
      fireProblemsAppeared(file);
    }
    else {
      fireProblemsChanged(file);
    }
  }


  @Override
  public void clearProblemsFromExternalSource(@NotNull VirtualFile file, @NotNull Object source) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    AtomicBoolean isLastExternalSource = new AtomicBoolean();
    myProblemsFromExternalSources.compute(file, (__, problems) -> {
      if (problems == null) return null;
      problems.remove(source);
      boolean wasLastProblem = problems.isEmpty();
      isLastExternalSource.set(wasLastProblem);
      return wasLastProblem ? null : problems;
    });


    if (isLastExternalSource.get() && !hasRegularProblems(file)) {
      fireProblemsDisappeared(file);
    }
    else {
      fireProblemsChanged(file);
    }
  }

  private static @NotNull TextRange getTextRange(@NotNull VirtualFile virtualFile, int line, int column) {
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assert document != null;
    int lineCount = document.getLineCount();
    if (line > lineCount) {
      line = lineCount;
    }
    line = line <= 0 ? 0 : line - 1;
    int offset = document.getLineStartOffset(line) + (column <= 0 ? 0 : column - 1);
    return new TextRange(offset, offset);
  }

  public void consumeProblemFiles(@NotNull Consumer<? super VirtualFile> consumer) {
    myProblems.keySet().forEach(consumer);
  }

  public void consumeProblemFilesFromExternalSources(@NotNull Consumer<? super VirtualFile> consumer) {
    myProblemsFromExternalSources.keySet().forEach(consumer);
  }

  @TestOnly
  public void waitForFilesQueuedForInvalidationAreProcessed() {
    myWolfListeners.waitForFilesQueuedForInvalidationAreProcessed();
  }
  @TestOnly
  public static @NotNull WolfTheProblemSolver createTestInstance(@NotNull Project project) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    //noinspection UsagesOfObsoleteApi
    return new WolfTheProblemSolverImpl(project, ((ComponentManagerEx)project).getCoroutineScope());
  }

}
