// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.concurrency.ThreadContext;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages execution of {@link UnresolvedReferenceQuickFixUpdater#registerQuickFixesLater(PsiReference, HighlightInfo.Builder)} in background.
 * The list of {@link HighlightInfo}s which require background quick fix computation is stored in document markup.
 * If {@link HighlightInfo#unresolvedReference} is not null, it means that {@link HighlightInfo} will need to compute its quickfixes in background.
 * That computation is started by {@link UnresolvedReferenceQuickFixUpdater#startComputingNextQuickFixes(PsiFile, Editor, ProperTextRange)}, which tries to start not too many jobs to conserve resources
 * and to avoid calculating quick fixes for HighlightInfo which will never be needed anyway.
 * Upon its completion for each info, its {@link HighlightInfo#UNRESOLVED_REFERENCE_QUICK_FIXES_COMPUTED_MASK} bit is set, which means its quickfixes are ready to be shown.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public final class UnresolvedReferenceQuickFixUpdaterImpl implements UnresolvedReferenceQuickFixUpdater {
  private static final Key<Future<?>> JOB = Key.create("JOB");
  private final Project myProject;
  private volatile boolean enabled = true;
  public UnresolvedReferenceQuickFixUpdaterImpl(Project project) {
    myProject = project;
  }

  public void waitQuickFixesSynchronously(@NotNull PsiFile file, @NotNull Editor editor, @NotNull List<? extends HighlightInfo> infos) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessNotAllowed(); // have to be able to wait over the write action to finish, must not hold RA for that
    for (HighlightInfo info : infos) {
      PsiReference reference = info.unresolvedReference;
      if (reference == null) continue;
      if (info.isUnresolvedReferenceQuickFixesComputed()) continue;
      PsiElement refElement = ReadAction.compute(() -> reference.getElement());
      Future<?> job = refElement.getUserData(JOB);
      if (job == null) {
        CompletableFuture<Object> newFuture = new CompletableFuture<>();
        job = ((UserDataHolderEx)refElement).putUserDataIfAbsent(JOB, newFuture);
        if (job == newFuture) {
          try {
            ReadAction.run(() -> registerReferenceFixes(info, editor, file, reference, new ProperTextRange(file.getTextRange())));
            newFuture.complete(null);
          }
          catch (Throwable t) {
            newFuture.completeExceptionally(t);
          }
        }
      }
      try {
        // wait outside RA
        job.get(100, TimeUnit.SECONDS);
      }
      catch (InterruptedException | ExecutionException | TimeoutException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void registerQuickFixesLater(@NotNull PsiReference ref, @NotNull HighlightInfo.Builder info) {
    ((HighlightInfoB)info).setUnresolvedReference(ref);
  }

  @Override
  public void registerQuickFixesLater(@NotNull PsiReference ref, @NotNull AnnotationBuilder builder) {
    ((B)builder).unresolvedReference(ref);
  }

  @Override
  public void startComputingNextQuickFixes(@NotNull PsiFile file, @NotNull Editor editor, @NotNull ProperTextRange visibleRange) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    int offset = editor.getCaretModel().getOffset();
    Project project = file.getProject();
    Document document = editor.getDocument();
    AtomicInteger unresolvedInfosProcessed = new AtomicInteger();
    // first, compute quick fixes close to the caret
    DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, offset,
                                           document.getTextLength(), info -> {
        if (!info.isUnresolvedReference()) {
          return true;
        }
        startUnresolvedRefsJob(info, editor, file, visibleRange);
        // start no more than two jobs
        return unresolvedInfosProcessed.incrementAndGet() <= 2;
      });
    // then, compute quickfixes inside the entire visible area, to show import hints for all unresolved references in vicinity if enabled
    if (DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled() &&
        DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(file)) {
      DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, visibleRange.getStartOffset(),
                                             visibleRange.getEndOffset(), info -> {
          if (info.isUnresolvedReference()) {
            startUnresolvedRefsJob(info, editor, file, visibleRange);
          }
          return true;
        });
    }
  }

  private void startUnresolvedRefsJob(@NotNull HighlightInfo info, @NotNull Editor editor, @NotNull PsiFile file,
                                      @NotNull ProperTextRange visibleRange) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!enabled) {
      return;
    }
    PsiReference reference = info.unresolvedReference;
    if (reference == null) return;
    PsiElement refElement = reference.getElement();
    Future<?> job = refElement.getUserData(JOB);
    if (job != null) {
      return;
    }
    if (info.isUnresolvedReferenceQuickFixesComputed()) return;
    job = ForkJoinPool.commonPool().submit(ThreadContext.captureThreadContext(() ->
      ((ApplicationImpl)ApplicationManager.getApplication()).executeByImpatientReader(
        () -> ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(
          () -> registerReferenceFixes(info, editor, file, reference, visibleRange), new DaemonProgressIndicator()))), null);
    refElement.putUserData(JOB, job);
  }

  private void registerReferenceFixes(@NotNull HighlightInfo info, @NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiReference reference,
                                      @NotNull ProperTextRange visibleRange) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiElement referenceElement = reference.getElement();
    if (myProject.isDisposed() || !file.isValid() || editor.isDisposed()
        || DumbService.getInstance(myProject).isDumb() || !referenceElement.isValid()) {
      // this will be restarted anyway on smart mode switch
      return;
    }
    AtomicBoolean changed = new AtomicBoolean();
    try {
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(
        reference, new QuickFixActionRegistrarImpl(info) {
          @Override
          void doRegister(@NotNull IntentionAction action,
                          @Nls(capitalization = Nls.Capitalization.Sentence) @Nullable String displayName,
                          @Nullable TextRange fixRange,
                          @Nullable HighlightDisplayKey key) {
            super.doRegister(action, displayName, fixRange, key);
            changed.set(true);
          }
        });
      info.setUnresolvedReferenceQuickFixesComputed();
    }
    finally {
      referenceElement.putUserData(JOB, null);
    }
    if (!changed.get() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }
    TextEditorHighlightingPass showAutoImportPass = new ShowAutoImportPass(file, editor, visibleRange);
    // have to restart ShowAutoImportPass manually because the highlighting session might very well be over by now
    ApplicationManager.getApplication().invokeLater(() -> {
      DaemonProgressIndicator sessionIndicator = new DaemonProgressIndicator();
      ProgressManager.getInstance().executeProcessUnderProgress(() -> showAutoImportPass.doApplyInformationToEditor(), sessionIndicator);
    }, __ -> editor.isDisposed() || file.getProject().isDisposed());
  }

  @TestOnly
  void stopUntil(@NotNull Disposable disposable) {
    enabled = false;
    Disposer.register(disposable, ()->enabled=true);
  }

  @TestOnly
  void waitForBackgroundJobIfStartedInTests(@NotNull HighlightInfo info) throws InterruptedException, ExecutionException, TimeoutException {
    PsiReference reference = info.unresolvedReference;
    if (reference == null) return;
    Future<?> job = reference.getElement().getUserData(JOB);
    if (job != null) {
      job.get(60, TimeUnit.SECONDS);
    }
  }
}
