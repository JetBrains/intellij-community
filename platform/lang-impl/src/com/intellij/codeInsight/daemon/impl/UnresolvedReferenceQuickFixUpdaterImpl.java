// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
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
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages execution of {@link UnresolvedReferenceQuickFixUpdater#registerQuickFixesLater(PsiReference, HighlightInfo)} in background.
 * The list of {@link HighlightInfo}s which require background quick fix computation is stored in document markup.
 * If {@link HighlightInfo#unresolvedReference} is not null, it means that {@link HighlightInfo} will need to compute its quickfixes in background.
 * That computation is started by {@link UnresolvedReferenceQuickFixUpdater#startComputingNextQuickFixes(PsiFile, Editor, ProperTextRange)}, which tries to start not too many jobs to conserve resources
 * and to avoid calculating quick fixes for HighlightInfo which will never be needed anyway.
 * Upon its completion for each info, its {@link HighlightInfo#UNRESOLVED_REFERENCE_QUICK_FIXES_COMPUTED_MASK} bit is set, which means its quickfixes are ready to be shown.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public class UnresolvedReferenceQuickFixUpdaterImpl implements UnresolvedReferenceQuickFixUpdater {
  private static final Key<Job<?>> JOB = Key.create("JOB");
  private final Project myProject;
  private volatile boolean enabled = true;
  public UnresolvedReferenceQuickFixUpdaterImpl(Project project) {
    myProject = project;
  }

  public void waitQuickFixesSynchronously(@NotNull HighlightInfo info, @NotNull PsiFile file, @NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    Job<?> job = startUnresolvedRefsJob(info, editor, file);
    if (job != null) {
      try {
        job.waitForCompletion(100_000);
      }
      catch (InterruptedException ignored) {
      }
    }
  }

  public void registerQuickFixesLater(@NotNull PsiReference ref, @NotNull HighlightInfo info) {
    info.setUnresolvedReference(ref);
  }

  @Override
  public void startComputingNextQuickFixes(@NotNull PsiFile file, @NotNull Editor editor, @NotNull ProperTextRange visibleRange) {
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
        startUnresolvedRefsJob(info, editor, file);
        // start no more than two jobs
        return unresolvedInfosProcessed.incrementAndGet() <= 2;
      });
    // then, compute quickfixes inside the entire visible area, to show import hints for all unresolved references in vicinity if enabled
    if (DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled() &&
        DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(file)) {
      DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, visibleRange.getStartOffset(),
                                             visibleRange.getEndOffset(), info -> {
          if (info.isUnresolvedReference()) {
            startUnresolvedRefsJob(info, editor, file);
          }
          return true;
        });
    }
  }

  private Job<?> startUnresolvedRefsJob(@NotNull HighlightInfo info, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!enabled) {
      return null;
    }
    PsiReference reference = info.unresolvedReference;
    if (reference == null) return null;
    Job<?> job = reference.getElement().getUserData(JOB);
    if (job != null) {
      return job;
    }
    if (info.isUnresolvedReferenceQuickFixesComputed()) return null;
    DaemonProgressIndicator indicator = new DaemonProgressIndicator();
    job = JobLauncher.getInstance().submitToJobThread(() ->
      ((ApplicationImpl)ApplicationManager.getApplication()).executeByImpatientReader(() ->
        ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
          if (DumbService.getInstance(myProject).isDumb()) {
            // this will be restarted anyway on smart mode switch
            return;
          }
          AtomicBoolean changed = new AtomicBoolean();
          UnresolvedReferenceQuickFixProvider.registerReferenceFixes(reference, new QuickFixActionRegistrarImpl(info) {
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
          reference.getElement().putUserData(JOB, null);
          if (changed.get()) {
            VirtualFile virtualFile = file.getVirtualFile();
            boolean isInContent = ModuleUtilCore.projectContainsFile(myProject, virtualFile, false);
            // have to restart ShowAutoImportPass manually because the highlighting session might very well be over by now
            ApplicationManager.getApplication().invokeLater(() -> {
              DaemonProgressIndicator sessionIndicator = new DaemonProgressIndicator();
              boolean canChangeFileSilently = CanISilentlyChange.thisFile(file).canIReally(isInContent);
              ProgressManager.getInstance().executeProcessUnderProgress(() ->
                HighlightingSessionImpl.runInsideHighlightingSession(file, null, ProperTextRange.create(file.getTextRange()), canChangeFileSilently,
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

  @TestOnly
  public void waitForBackgroundJobIfStartedInTests(@NotNull HighlightInfo info) throws InterruptedException {
    PsiReference reference = info.unresolvedReference;
    if (reference == null) return;
    Job<?> job = reference.getElement().getUserData(JOB);
    if (job == null) {
      return;
    }
    job.waitForCompletion(60_000);
  }
}
