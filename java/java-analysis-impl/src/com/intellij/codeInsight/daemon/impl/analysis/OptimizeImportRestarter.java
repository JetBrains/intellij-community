// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Listen for daemon finish events and schedule {@link com.intellij.codeInsight.intention.impl.config.QuickFixFactoryImpl.OptimizeImportsFix} to run in EDT if they are still relevant.
 * This service is needed for batching all these import fix requests in one place to avoid overhead of registering individual Disposables/listeners for each file in {@link UnusedImportsVisitor}
 */
@VisibleForTesting
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
public final class OptimizeImportRestarter implements Disposable {
  private final Project myProject;
  private final List<OptimizeRequest> queue = new ArrayList<>(); // guarded by queue
  private final Set<Future<?>> scheduledFutures = ContainerUtil.newConcurrentSet(); // futures (from RA.nonblocking) of requests for autoimports are stored here to be able to wait for them in tests

  private record OptimizeRequest(@NotNull PsiFile psiFile, long modificationStampBefore, @NotNull ModCommandAction optimizeFix) {
  }

  @ApiStatus.Internal
  public static OptimizeImportRestarter getInstance(Project project) {
    return project.getService(OptimizeImportRestarter.class);
  }

  OptimizeImportRestarter(Project project) {
    myProject = project;
    myProject.getMessageBus().connect(this).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
      @Override
      public void daemonFinished(@NotNull Collection<? extends FileEditor> __) {
        onDaemonFinished();
      }
    });
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new PsiTreeAnyChangeAbstractAdapter() {
      @Override
      protected void onChange(@Nullable PsiFile __) {
        // something changed, cancel everything because the fix might be invalid, and the highlighting will restart anyway
        clear();
      }
    }, this);
  }

  private void clear() {
    synchronized (queue) {
      queue.clear();
      for (Future<?> future : scheduledFutures) {
        future.cancel(false);
      }
      scheduledFutures.clear();
    }
  }

  private void onDaemonFinished() {
    List<OptimizeRequest> requests;

    synchronized (queue) {
      requests = new ArrayList<>(queue);
      queue.clear();
    }
    for (OptimizeRequest request : requests) {
      if (myProject.isDisposed()) {
        break;
      }
      PsiFile psiFile = request.psiFile();
      if (psiFile.getModificationStamp() != request.modificationStampBefore()) {
        continue;
      }
      if (!psiFile.isWritable()) {
        continue;
      }
      ModCommandAction optimizeFix = request.optimizeFix();
      if (!DaemonCodeAnalyzerEx.getInstanceEx(myProject).isErrorAnalyzingFinished(psiFile)) {
        // re-fire when daemon is really finished
        scheduleOnDaemonFinish(psiFile, optimizeFix);
        continue;
      }
      ActionContext context = ActionContext.from(null, psiFile);
      if (optimizeFix.getPresentation(context) == null) {
        continue;
      }
      CancellablePromise<@NotNull ModCommand> future =
        ReadAction.nonBlocking(() -> optimizeFix.getPresentation(context) != null ? optimizeFix.perform(context) : ModCommand.nop())
          .expireWhen(() -> myProject.isDisposed() || psiFile.getModificationStamp() != request.modificationStampBefore())
          .finishOnUiThread(ModalityState.defaultModalityState(),
                            command -> CommandProcessor.getInstance().executeCommand(
                              myProject, () -> ModCommandExecutor.getInstance().executeInBatch(context, command),
                              CodeInsightBundle.message("process.optimize.imports"), null))
          .submit(AppExecutorUtil.getAppExecutorService());
      scheduledFutures.add(future);
      future.onProcessed(__->scheduledFutures.remove(future));
    }
  }

  void scheduleOnDaemonFinish(@NotNull PsiFile psiFile, @NotNull ModCommandAction optimizeFix) {
    long modificationStampBefore = psiFile.getModificationStamp();
    synchronized (queue) {
      queue.add(new OptimizeRequest(psiFile, modificationStampBefore, optimizeFix));
    }
  }

  @RequiresBackgroundThread
  @TestOnly
  public void waitForScheduledOptimizeImportRequestsInTests() throws ExecutionException, InterruptedException {
    assert ApplicationManager.getApplication().isUnitTestMode();
    ThreadingAssertions.assertBackgroundThread(); // because futures are completed in EDT
    ConcurrencyUtil.getAll(scheduledFutures);
  }

  @Override
  public void dispose() {
    clear();
  }
}
