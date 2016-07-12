/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.problems;

import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.*;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author cdr
 */
public class WolfTheProblemSolverImpl extends WolfTheProblemSolver {
  private final Map<VirtualFile, ProblemFileInfo> myProblems = new THashMap<VirtualFile, ProblemFileInfo>(); // guarded by myProblems
  private final Collection<VirtualFile> myCheckingQueue = new THashSet<VirtualFile>(10);

  private final Project myProject;
  private final List<ProblemListener> myProblemListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<Condition<VirtualFile>> myFilters = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myFiltersLoaded = false;
  private final ProblemListener fireProblemListeners = new ProblemListener() {
    @Override
    public void problemsAppeared(@NotNull VirtualFile file) {
      for (final ProblemListener problemListener : myProblemListeners) {
        problemListener.problemsAppeared(file);
      }
    }

    @Override
    public void problemsChanged(@NotNull VirtualFile file) {
      for (final ProblemListener problemListener : myProblemListeners) {
        problemListener.problemsChanged(file);
      }
    }

    @Override
    public void problemsDisappeared(@NotNull VirtualFile file) {
      for (final ProblemListener problemListener : myProblemListeners) {
        problemListener.problemsDisappeared(file);
      }
    }
  };

  private void doRemove(@NotNull VirtualFile problemFile) {
    ProblemFileInfo old;
    synchronized (myProblems) {
      old = myProblems.remove(problemFile);
    }
    synchronized (myCheckingQueue) {
      myCheckingQueue.remove(problemFile);
    }
    if (old != null) {
      // firing outside lock
      fireProblemListeners.problemsDisappeared(problemFile);
    }
  }

  private static class ProblemFileInfo {
    private final Collection<Problem> problems = new THashSet<Problem>();
    private boolean hasSyntaxErrors;

    public boolean equals(@Nullable final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ProblemFileInfo that = (ProblemFileInfo)o;

      return hasSyntaxErrors == that.hasSyntaxErrors && problems.equals(that.problems);
    }

    public int hashCode() {
      int result = problems.hashCode();
      result = 31 * result + (hasSyntaxErrors ? 1 : 0);
      return result;
    }
  }

  public WolfTheProblemSolverImpl(@NotNull Project project,
                                  @NotNull PsiManager psiManager,
                                  @NotNull VirtualFileManager virtualFileManager) {
    myProject = project;
    PsiTreeChangeListener changeListener = new PsiTreeChangeAdapter() {
      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        clearSyntaxErrorFlag(event);
      }
    };
    psiManager.addPsiTreeChangeListener(changeListener);
    VirtualFileListener virtualFileListener = new VirtualFileAdapter() {
      @Override
      public void fileDeleted(@NotNull final VirtualFileEvent event) {
        onDeleted(event.getFile());
      }

      @Override
      public void fileMoved(@NotNull final VirtualFileMoveEvent event) {
        onDeleted(event.getFile());
      }

      private void onDeleted(@NotNull final VirtualFile file) {
        if (file.isDirectory()) {
          clearInvalidFiles();
        }
        else {
          doRemove(file);
        }
      }
    };
    virtualFileManager.addVirtualFileListener(virtualFileListener, myProject);
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) { //tests?
      fileStatusManager.addFileStatusListener(new FileStatusListener() {
        @Override
        public void fileStatusesChanged() {
          clearInvalidFiles();
        }

        @Override
        public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
          fileStatusesChanged();
        }
      });
    }
  }

  private void clearInvalidFiles() {
    VirtualFile[] files;
    synchronized (myProblems) {
      files = VfsUtilCore.toVirtualFileArray(myProblems.keySet());
    }
    for (VirtualFile problemFile : files) {
      if (!problemFile.isValid() || !isToBeHighlighted(problemFile)) {
        doRemove(problemFile);
      }
    }
  }

  private void clearSyntaxErrorFlag(@NotNull final PsiTreeChangeEvent event) {
    PsiFile file = event.getFile();
    if (file == null) return;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    synchronized (myProblems) {
      ProblemFileInfo info = myProblems.get(virtualFile);
      if (info != null) {
        info.hasSyntaxErrors = false;
      }
    }
  }

  public void startCheckingIfVincentSolvedProblemsYet(@NotNull ProgressIndicator progress,
                                                      @NotNull ProgressableTextEditorHighlightingPass pass)
    throws ProcessCanceledException {
    if (!myProject.isOpen()) return;

    List<VirtualFile> files;
    synchronized (myCheckingQueue) {
      files = new ArrayList<VirtualFile>(myCheckingQueue);
    }
    long progressLimit = 0;
    for (VirtualFile file : files) {
      if (file.isValid()) {
        progressLimit += file.getLength(); // (rough approx number of PSI elements = file length/2) * (visitor count = 2 usually)
      }
    }
    pass.setProgressLimit(progressLimit);
    for (final VirtualFile virtualFile : files) {
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
  private boolean orderVincentToCleanTheCar(@NotNull final VirtualFile file,
                                            @NotNull final ProgressIndicator progressIndicator) throws ProcessCanceledException {
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
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return false;
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return false;

    final AtomicReference<HighlightInfo> error = new AtomicReference<HighlightInfo>();
    final AtomicBoolean hasErrorElement = new AtomicBoolean();
    try {
      GeneralHighlightingPass pass = new GeneralHighlightingPass(myProject, psiFile, document, 0, document.getTextLength(),
                                                                 false, new ProperTextRange(0, document.getTextLength()), null, HighlightInfoProcessor.getEmpty()) {
        @Override
        protected HighlightInfoHolder createInfoHolder(@NotNull final PsiFile file) {
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
        reportProblems(file, Collections.<Problem>singleton(problem));
      }
      return false;
    }
    clearProblems(file);
    return true;
  }

  @Override
  public boolean hasSyntaxErrors(final VirtualFile file) {
    synchronized (myProblems) {
      ProblemFileInfo info = myProblems.get(file);
      return info != null && info.hasSyntaxErrors;
    }
  }

  private boolean willBeHighlightedAnyway(final VirtualFile file) {
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
  public boolean hasProblemFilesBeneath(@NotNull Condition<VirtualFile> condition) {
    if (!myProject.isOpen()) return false;
    synchronized (myProblems) {
      if (!myProblems.isEmpty()) {
        for (VirtualFile problemFile : myProblems.keySet()) {
          if (problemFile.isValid() && condition.value(problemFile)) return true;
        }
      }
      return false;
    }
  }

  @Override
  public boolean hasProblemFilesBeneath(@NotNull final Module scope) {
    return hasProblemFilesBeneath(virtualFile -> ModuleUtilCore.moduleContainsFile(scope, virtualFile, false));
  }

  @Override
  public void addProblemListener(@NotNull ProblemListener listener) {
    myProblemListeners.add(listener);
  }

  @Override
  public void addProblemListener(@NotNull final ProblemListener listener, @NotNull Disposable parentDisposable) {
    addProblemListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeProblemListener(listener);
      }
    });
  }

  @Override
  public void removeProblemListener(@NotNull ProblemListener listener) {
    myProblemListeners.remove(listener);
  }

  @Override
  public void registerFileHighlightFilter(@NotNull final Condition<VirtualFile> filter, @NotNull Disposable parentDisposable) {
    myFilters.add(filter);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myFilters.remove(filter);
      }
    });
  }

  @Override
  public void queue(VirtualFile suspiciousFile) {
    if (!isToBeHighlighted(suspiciousFile)) return;
    doQueue(suspiciousFile);
  }

  private void doQueue(@NotNull VirtualFile suspiciousFile) {
    synchronized (myCheckingQueue) {
      myCheckingQueue.add(suspiciousFile);
    }
  }

  @Override
  public boolean isProblemFile(VirtualFile virtualFile) {
    synchronized (myProblems) {
      return myProblems.containsKey(virtualFile);
    }
  }

  private boolean isToBeHighlighted(@Nullable VirtualFile virtualFile) {
    if (virtualFile == null) return false;

    synchronized (myFilters) {
      if (!myFiltersLoaded) {
        myFiltersLoaded = true;
        myFilters.addAll(Arrays.asList(Extensions.getExtensions(FILTER_EP_NAME, myProject)));
      }
    }
    for (final Condition<VirtualFile> filter : myFilters) {
      ProgressManager.checkCanceled();
      if (filter.value(virtualFile)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void weHaveGotProblems(@NotNull final VirtualFile virtualFile, @NotNull List<Problem> problems) {
    if (problems.isEmpty()) return;
    if (!isToBeHighlighted(virtualFile)) return;
    weHaveGotNonIgnorableProblems(virtualFile, problems);
  }

  @Override
  public void weHaveGotNonIgnorableProblems(@NotNull VirtualFile virtualFile, @NotNull List<Problem> problems) {
    if (problems.isEmpty()) return;
    boolean fireListener = false;
    synchronized (myProblems) {
      ProblemFileInfo storedProblems = myProblems.get(virtualFile);
      if (storedProblems == null) {
        storedProblems = new ProblemFileInfo();

        myProblems.put(virtualFile, storedProblems);
        fireListener = true;
      }
      storedProblems.problems.addAll(problems);
    }
    doQueue(virtualFile);
    if (fireListener) {
      fireProblemListeners.problemsAppeared(virtualFile);
    }
  }

  @Override
  public void clearProblems(@NotNull VirtualFile virtualFile) {
    doRemove(virtualFile);
  }

  @Override
  public Problem convertToProblem(@Nullable final VirtualFile virtualFile,
                                  final int line,
                                  final int column,
                                  @NotNull final String[] message) {
    if (virtualFile == null || virtualFile.isDirectory() || virtualFile.getFileType().isBinary()) return null;
    HighlightInfo info = ApplicationManager.getApplication().runReadAction(new Computable<HighlightInfo>() {
      @Override
      public HighlightInfo compute() {
        TextRange textRange = getTextRange(virtualFile, line, column);
        String description = StringUtil.join(message, "\n");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
      }
    });
    if (info == null) return null;
    return new ProblemImpl(virtualFile, info, false);
  }

  @Override
  public void reportProblems(@NotNull final VirtualFile file, @NotNull Collection<Problem> problems) {
    if (problems.isEmpty()) {
      clearProblems(file);
      return;
    }
    if (!isToBeHighlighted(file)) return;
    boolean hasProblemsBefore;
    boolean fireChanged;
    synchronized (myProblems) {
      final ProblemFileInfo oldInfo = myProblems.remove(file);
      hasProblemsBefore = oldInfo != null;
      ProblemFileInfo newInfo = new ProblemFileInfo();
      myProblems.put(file, newInfo);
      for (Problem problem : problems) {
        newInfo.problems.add(problem);
        newInfo.hasSyntaxErrors |= ((ProblemImpl)problem).isSyntaxOnly();
      }
      fireChanged = hasProblemsBefore && !oldInfo.equals(newInfo);
    }
    doQueue(file);
    if (!hasProblemsBefore) {
      fireProblemListeners.problemsAppeared(file);
    }
    else if (fireChanged) {
      fireProblemListeners.problemsChanged(file);
    }
  }

  @NotNull
  private static TextRange getTextRange(@NotNull final VirtualFile virtualFile, int line, final int column) {
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (line > document.getLineCount()) line = document.getLineCount();
    line = line <= 0 ? 0 : line - 1;
    int offset = document.getLineStartOffset(line) + (column <= 0 ? 0 : column - 1);
    return new TextRange(offset, offset);
  }
}
