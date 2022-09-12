// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixActionRegistrarImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages execution of {@link UnresolvedReferenceQuickFixUpdater#registerQuickFixesLater(PsiReference, HighlightInfo)} in background.
 * The list of {@link HighlightInfo}s which require background quick fix computation is stored in document markup.
 * If {@link HighlightInfo#unresolvedReference} is not null, it means that {@link HighlightInfo} will need to compute its quickfixes in background.
 * That computation is started by {@link #startComputingNextQuickFixes(PsiFile, Editor)}, which tries to start not too many jobs to conserve resources
 * and to avoid calculating quick fixes for HighlightInfo which will never be needed anyway.
 * Upon its completion for each info, its {@link HighlightInfo#UNRESOLVED_REFERENCE_QUICK_FIXES_COMPUTED_MASK} bit is set, which means its quickfixes are ready to be shown.
 */
@ApiStatus.Internal
public class UnresolvedReferenceQuickFixUpdaterImpl implements UnresolvedReferenceQuickFixUpdater {
  private static final Key<Job<?>> JOB = Key.create("JOB");
  private final Project myProject;
  private volatile boolean enabled = true;
  public UnresolvedReferenceQuickFixUpdaterImpl(Project project) {
    myProject = project;
  }

  public void waitQuickFixesSynchronously(@NotNull HighlightInfo info, @NotNull PsiFile file, @NotNull Editor editor) {
    //ApplicationManager.getApplication().assertIsDispatchThread();
    Job<?> job = startUnresolvedRefsJob(info, editor, file);
    if (job != null) {
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          job.waitForCompletion(100_000);
        }
        catch (InterruptedException ignored) {
        }
      });
      try {
        future.get();
      }
      catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void registerQuickFixesLater(@NotNull PsiReference ref, @NotNull HighlightInfo info) {
    info.setUnresolvedReference(ref);
  }

  @Override
  public void startComputingNextQuickFixes(@NotNull PsiFile file, @NotNull Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    Project project = file.getProject();
    Document document = editor.getDocument();
    AtomicInteger unresolvedInfosProcessed = new AtomicInteger();
    DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.INFORMATION, offset,
                                           document.getTextLength(), info -> {
        if (!info.isUnresolvedReference()) {
          return true;
        }
        startUnresolvedRefsJob(info, editor, file);
        // start no more than two jobs
        return unresolvedInfosProcessed.incrementAndGet() <= 2;
      });
  }

  private Job<?> startUnresolvedRefsJob(@NotNull HighlightInfo info, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!enabled) {
      return null;
    }
    PsiReference reference = info.unresolvedReference;
    if (reference == null) return null;
    if (info.isUnresolvedReferenceQuickFixesComputed()) return null;
    DaemonProgressIndicator indicator = new DaemonProgressIndicator();
    Job<?> job = JobLauncher.getInstance().submitToJobThread(() ->
      ((ApplicationImpl)ApplicationManager.getApplication()).executeByImpatientReader(() ->
        ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
          if (DumbService.getInstance(myProject).isDumb()) {
            // this will be restarted anyway on smart mode switch
            return;
          }
          AtomicBoolean changed = new AtomicBoolean();
          UnresolvedReferenceQuickFixProvider.registerReferenceFixes(reference, new QuickFixActionRegistrarImpl(info) {
            @Override
            public void register(@NotNull TextRange fixRange, @NotNull IntentionAction action, HighlightDisplayKey key) {
              super.register(fixRange, action, key);
              changed.set(true);
            }
          });
          info.setUnresolvedReferenceQuickFixesComputed();
          reference.getElement().putUserData(JOB, null);
          if (changed.get()) {
            // have to restart ShowAutoImportPass manually because the highlighting session might very well be over by now
            ApplicationManager.getApplication().invokeLater(() -> {
              DaemonProgressIndicator sessionIndicator = new DaemonProgressIndicator();
              ProgressManager.getInstance().executeProcessUnderProgress(() ->
                HighlightingSessionImpl.runInsideHighlightingSession(file, sessionIndicator, null, ProperTextRange.create(file.getTextRange()), CanISilentlyChange.thisFile(file),
                                                                     () -> DefaultHighlightInfoProcessor.showAutoImportHints(editor, file, sessionIndicator))
              , sessionIndicator);
            }, __->editor.isDisposed() || file.getProject().isDisposed());
          }
        }, indicator)), null);
    reference.getElement().putUserData(JOB, job);
    return job;
  }

  @TestOnly
  public void stopUntil(@NotNull Disposable disposable) {
    enabled = false;
    Disposer.register(disposable, ()->enabled=true);
  }
}
