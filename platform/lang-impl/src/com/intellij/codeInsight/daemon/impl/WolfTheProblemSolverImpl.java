// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.problems.ProblemImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WolfTheProblemSolverImpl extends WolfTheProblemSolver implements Disposable {
  private final Map<VirtualFile, ProblemFileInfo> myProblems = new ConcurrentHashMap<>();
  private final Map<VirtualFile, Set<Object>> myProblemsFromExternalSources = new ConcurrentHashMap<>();
  private final Collection<VirtualFile> myCheckingQueue = new HashSet<>(10); // guarded by myCheckingQueue

  private final Project myProject;
  private final WolfListeners myWolfListeners;

  private WolfTheProblemSolverImpl(@NotNull Project project) {
    myProject = project;
    myWolfListeners = new WolfListeners(project, this);
    Disposer.register(this, myWolfListeners);
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

  private static class ProblemFileInfo {
    private final Collection<Problem> problems = ContainerUtil.newConcurrentSet();
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
    PsiFile file = event.getFile();
    if (file == null) return;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    ProblemFileInfo info = myProblems.get(virtualFile);
    if (info != null) {
      info.hasSyntaxErrors = false;
    }
  }

  void startCheckingIfVincentSolvedProblemsYet(@NotNull ProgressIndicator progress, @NotNull ProgressableTextEditorHighlightingPass pass)
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

  // returns true if car has been cleaned
  private boolean orderVincentToCleanTheCar(@NotNull VirtualFile file, @NotNull ProgressIndicator progressIndicator) throws ProcessCanceledException {
    if (!isToBeHighlighted(file)) {
      clearProblems(file);
      return true; // file is going to be red waved no more
    }
    if (hasSyntaxErrors(file)) {
      // optimization: it's no use anyway to try clean the file with syntax errors, only changing the file itself can help
      return false;
    }
    if (myProject.isDisposed()) return false;
    if (willBeHighlightedAnyway(file)) return false;
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return false;
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return false;

    AtomicReference<HighlightInfo> error = new AtomicReference<>();
    AtomicBoolean hasErrorElement = new AtomicBoolean();
    try {
      ProperTextRange visibleRange = new ProperTextRange(0, document.getTextLength());
      HighlightingSessionImpl.getOrCreateHighlightingSession(psiFile, (DaemonProgressIndicator)progressIndicator, visibleRange);
      GeneralHighlightingPass pass = new GeneralHighlightingPass(psiFile, document, 0, document.getTextLength(),
                                                                 false, visibleRange, null, HighlightInfoProcessor.getEmpty()) {
        @NotNull
        @Override
        protected HighlightInfoHolder createInfoHolder(@NotNull PsiFile file) {
          return new HighlightInfoHolder(file) {
            @Override
            public boolean add(@Nullable HighlightInfo info) {
              if (info != null && info.getSeverity() == HighlightSeverity.ERROR) {
                error.set(info);
                hasErrorElement.set(myHasErrorElement);
                throw new ProcessCanceledException();
              }
              return super.add(info);
            }
          };
        }
      };
      pass.collectInformation(progressIndicator);
    }
    catch (ProcessCanceledException e) {
      if (error.get() != null) {
        ProblemImpl problem = new ProblemImpl(file, error.get(), hasErrorElement.get());
        reportProblems(file, Collections.singleton(problem));
      }
      return false;
    }
    clearProblems(file);
    return true;
  }

  @Override
  public boolean hasSyntaxErrors(@NotNull VirtualFile file) {
    ProblemFileInfo info = myProblems.get(file);
    return info != null && info.hasSyntaxErrors;
  }

  private boolean willBeHighlightedAnyway(@NotNull VirtualFile file) {
    // opened in some editor, and hence will be highlighted automatically sometime later
    FileEditor[] selectedEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
    for (FileEditor editor : selectedEditors) {
      if (!(editor instanceof TextEditor)) continue;
      Document document = ((TextEditor)editor).getEditor().getDocument();
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
  public void addProblemListener(@NotNull WolfTheProblemSolver.ProblemListener listener, @NotNull Disposable parentDisposable) {
    myProject.getMessageBus().connect(parentDisposable).subscribe(com.intellij.problems.ProblemListener.TOPIC, listener);
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
      for (Condition<VirtualFile> filter : FILTER_EP_NAME.getExtensions(myProject)) {
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
    if (!isToBeHighlighted(virtualFile)) return;
    weHaveGotNonIgnorableProblems(virtualFile, problems);
  }

  @Override
  public void weHaveGotNonIgnorableProblems(@NotNull VirtualFile virtualFile, @NotNull List<? extends Problem> problems) {
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

  private void fireProblemsAppeared(@NotNull VirtualFile file) {
    myProject.getMessageBus().syncPublisher(com.intellij.problems.ProblemListener.TOPIC).problemsAppeared(file);
  }

  private void fireProblemsChanged(@NotNull VirtualFile virtualFile) {
    myProject.getMessageBus().syncPublisher(com.intellij.problems.ProblemListener.TOPIC).problemsChanged(virtualFile);
  }

  private void fireProblemsDisappeared(@NotNull VirtualFile problemFile) {
    myProject.getMessageBus().syncPublisher(com.intellij.problems.ProblemListener.TOPIC).problemsDisappeared(problemFile);
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

    Set<Object> problems = myProblemsFromExternalSources.computeIfAbsent(file, __ -> ContainerUtil.newConcurrentSet());
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

  @NotNull
  private static TextRange getTextRange(@NotNull VirtualFile virtualFile, int line, int column) {
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (line > document.getLineCount()) line = document.getLineCount();
    line = line <= 0 ? 0 : line - 1;
    int offset = document.getLineStartOffset(line) + (column <= 0 ? 0 : column - 1);
    return new TextRange(offset, offset);
  }

  public boolean processProblemFiles(@NotNull Processor<? super VirtualFile> processor) {
    return ContainerUtil.process(myProblems.keySet(), processor);
  }
  public boolean processProblemFilesFromExternalSources(@NotNull Processor<? super VirtualFile> processor) {
    return ContainerUtil.process(myProblemsFromExternalSources.keySet(), processor);
  }

  @NotNull
  @TestOnly
  public static WolfTheProblemSolver createTestInstance(@NotNull Project project){
    assert ApplicationManager.getApplication().isUnitTestMode();
    return new WolfTheProblemSolverImpl(project);
  }
  @TestOnly
  void waitForFilesQueuedForInvalidationAreProcessed() {
    myWolfListeners.waitForFilesQueuedForInvalidationAreProcessed();
  }
}
