// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater;
import com.intellij.codeWithMe.ClientId;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages computation of lazy quick fixes registered via {@link HighlightInfo.Builder#registerLazyFixes(Consumer)} in background.
 * The list of lazy quick fixes that require background computation is stored in {@link HighlightInfo#myLazyQuickFixes}.
 * If {@link HighlightInfo#hasLazyQuickFixes()} is true, it means that {@link HighlightInfo} will need to compute its quickfixes in background.
 * That computation is started by {@link LazyQuickFixUpdater#startComputingNextQuickFixes(PsiFile, Editor, ProperTextRange)}, which tries to start not too many jobs to conserve resources
 * and to avoid calculating quick fixes for HighlightInfo which will never be needed anyway.
 * Upon its completion for each info, the futures in {@link HighlightInfo#myLazyQuickFixes} are computed, which means its quickfixes are ready to be shown.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public final class LazyQuickFixUpdaterImpl implements LazyQuickFixUpdater {
  private volatile boolean enabled = true;

  @Override
  public void waitQuickFixesSynchronously(@NotNull PsiFile file, @NotNull Editor editor, @NotNull HighlightInfo info) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ThreadingAssertions.assertNoOwnReadAccess(); // have to be able to wait over the write action to finish, must not hold RA for that

    ReadAction.run(() -> {
      try {
        info.computeQuickFixesSynchronously();
      }
      catch (ExecutionException | InterruptedException ignored) {

      }
    });
  }

  @Override
  public void startComputingNextQuickFixes(@NotNull PsiFile file, @NotNull Editor editor, @NotNull ProperTextRange visibleRange) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Project project = file.getProject();
    Document document = editor.getDocument();
    // compute unresolved refs suggestions from the caret to two pages down (in case the user is scrolling down, which is often the case)
    int startOffset = Math.max(0, visibleRange.getStartOffset());
    int endOffset = Math.min(document.getTextLength(), visibleRange.getEndOffset()+visibleRange.getLength());
    List<HighlightInfo> unresolvedInfos = new ArrayList<>(); // collect unresolved-ref infos into intermediate collection to avoid messing with HighlightInfo lock under the markup lock
    int caret = editor.getCaretModel().getOffset();
    TextRange caretLine = DocumentUtil.getLineTextRange(document, document.getLineNumber(caret));
    if (caretLine.getStartOffset() < startOffset || caretLine.getEndOffset() >= endOffset) {
      // in case the caret line is scrolled out of sight it would be useful if the quick fixes are ready there nevertheless
      DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, caretLine.getStartOffset(), caretLine.getEndOffset(), info -> {
        if (info.hasLazyQuickFixes()) {
          unresolvedInfos.add(info);
        }
        return true;
      });
    }
    DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, startOffset, endOffset, info -> {
      if (info.hasLazyQuickFixes()) {
        unresolvedInfos.add(info);
      }
      return true;
    });
    if (!ClientId.isLocal(ClientEditorManager.getClientId(editor))) {
      // for non-local editor its visible area is unreliable, so ignore all optimizations there
      // (see IJPL-163871 Intentions sometimes don't appear in Remote Dev and Code With Me)
      DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, 0, document.getTextLength(), info -> {
        if (info.hasLazyQuickFixes()) {
          unresolvedInfos.add(info);
        }
        return true;
      });
    }
    for (HighlightInfo info : unresolvedInfos) {
      startJob(info, editor, file, visibleRange);
    }
  }

  private void startJob(@NotNull HighlightInfo info,
                        @NotNull Editor editor,
                        @NotNull PsiFile file,
                        @NotNull ProperTextRange visibleRange) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!enabled) {
      return;
    }
    info.startComputeQuickFixes(computation -> {
      return ReadAction.nonBlocking(()->{
        AtomicReference<List<HighlightInfo.IntentionActionDescriptor>> result = new AtomicReference<>(List.of());
        ((ApplicationImpl)ApplicationManager.getApplication()).executeByImpatientReader(
          () -> ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(
            () -> {
              result.set(info.doComputeLazyQuickFixes(computation));
            }, new DaemonProgressIndicator()));
        return result.get();
      }).submit(ForkJoinPool.commonPool());
      }
    );
  }

  @TestOnly
  void stopUntil(@NotNull Disposable disposable) {
    enabled = false;
    Disposer.register(disposable, ()->enabled=true);
  }

  @TestOnly
  void waitForBackgroundJobIfStartedInTests(@NotNull HighlightInfo info, int timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    ApplicationManager.getApplication().assertIsDispatchThread();

    ReadAction.nonBlocking(() -> {
      try {
        info.computeQuickFixesSynchronously();
      }
      catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }).submit(AppExecutorUtil.getAppExecutorService()).get(timeout, unit);
  }
}
