// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class MainPassesRunner {
  private static final Logger LOG = Logger.getInstance(MainPassesRunner.class);
  private final Project myProject;
  private final @NlsContexts.DialogTitle String myTitle;
  private final InspectionProfile myInspectionProfile;

  public MainPassesRunner(@NotNull Project project,
                          @NlsContexts.DialogTitle @NotNull String title,
                          @Nullable InspectionProfile inspectionProfile) {
    myProject = project;
    myTitle = title;
    myInspectionProfile = inspectionProfile;
  }

  public @NotNull Map<Document, List<HighlightInfo>> runMainPasses(@NotNull List<? extends VirtualFile> filesToCheck) {
    return runMainPasses(filesToCheck, null);
  }
  @NotNull
  public Map<Document, List<HighlightInfo>> runMainPasses(@NotNull List<? extends VirtualFile> filesToCheck, @Nullable HighlightSeverity minimumSeverity) {
    Map<Document, List<HighlightInfo>> result = new ConcurrentHashMap<>();
    if (ApplicationManager.getApplication().isDispatchThread()) {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
        throw new RuntimeException("Must not run under write action");
      }
      Ref<Exception> exception = Ref.create();
      ProgressManager.getInstance().run(new Task.Modal(myProject, myTitle, true) {
        @Override
        public void run(@NotNull ProgressIndicator progress) {
          try {
            runMainPasses(filesToCheck, result, progress, minimumSeverity);
          }
          catch (ProcessCanceledException e) {
            LOG.info("Code analysis canceled", e);
            exception.set(e);
          }
          catch (Exception e) {
            LOG.error(e);
            exception.set(e);
          }
        }
      });
      if (!exception.isNull()) {
        ExceptionUtil.rethrowAllAsUnchecked(exception.get());
      }
    }
    else if (ProgressManager.getInstance().hasProgressIndicator()) {
      runMainPasses(filesToCheck, result, ProgressManager.getInstance().getProgressIndicator(), minimumSeverity);
    }
    else {
      throw new RuntimeException("Must run from Event Dispatch Thread or with a progress indicator");
    }

    return result;
  }

  private void runMainPasses(@NotNull List<? extends VirtualFile> files,
                             @NotNull Map<? super Document, ? super List<HighlightInfo>> result,
                             @NotNull ProgressIndicator progress,
                             @Nullable HighlightSeverity minimumSeverity) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    progress.setIndeterminate(false);
    while (true) {
      List<Pair<VirtualFile, DaemonProgressIndicator>> daemonIndicators = ContainerUtil.map(files, file -> Pair.create(file, new DaemonProgressIndicator()));
      Disposable disposable = Disposer.newDisposable();
      try {
        ReadAction.run(() -> {
          ((ProgressIndicatorEx)progress).addStateDelegate(new AbstractProgressIndicatorExBase() {
            @Override
            public void cancel() {
              super.cancel();
              daemonIndicators.forEach(daemonIndicator -> daemonIndicator.getSecond().cancel());
            }
          });
          ApplicationManager.getApplication().addApplicationListener(new ApplicationListener() {
            @Override
            public void beforeWriteActionStart(@NotNull Object action) {
              if (!progress.isCanceled()) {
                progress.cancel(); // that will cancel all daemonIndicators too
              }
            }
          }, disposable);
          // there is a chance we are racing with the write action, in which case just registered listener might not be called, retry.
          if (ApplicationManagerEx.getApplicationEx().isWriteActionPending()) {
            throw new ProcessCanceledException();
          }
        });

        AtomicInteger filesCompleted = new AtomicInteger();
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(daemonIndicators, progress, pair -> {
          VirtualFile file = pair.getFirst();
          progress.setText(ReadAction.compute(() -> ProjectUtil.calcRelativeToProjectPath(file, myProject)));
          DaemonProgressIndicator daemonIndicator = pair.getSecond();
          runMainPasses(file, result, daemonIndicator, minimumSeverity);
          int completed = filesCompleted.incrementAndGet();
          progress.setFraction((double)completed / files.size());
          return true;
        });
        break;
      }
      catch (ProcessCanceledException e) {
        if (progress.isCanceled()) {
          throw e;
        }
        //retry if the daemonIndicator was canceled by started write action
      }
      finally {
        Disposer.dispose(disposable);
      }
    }
  }

  private void runMainPasses(@NotNull VirtualFile file,
                             @NotNull Map<? super Document, ? super List<HighlightInfo>> result,
                             @NotNull DaemonProgressIndicator daemonIndicator,
                             @Nullable HighlightSeverity minimumSeverity) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(file));
    Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
    if (psiFile == null || document == null || !ReadAction.compute(() -> ProblemHighlightFilter.shouldProcessFileInBatch(psiFile))) {
      return;
    }
    ProperTextRange range = ProperTextRange.create(0, document.getTextLength());
    HighlightingSessionImpl session = HighlightingSessionImpl.createHighlightingSession(psiFile, daemonIndicator, null,
                                                                                        range, CanISilentlyChange.Result.UH_UH, 0);
    session.setMinimumSeverity(minimumSeverity);
    ProgressManager.getInstance().runProcess(() -> runMainPasses(daemonIndicator, result, psiFile, document), daemonIndicator);
  }

  private void runMainPasses(@NotNull ProgressIndicator daemonIndicator,
                             @NotNull Map<? super Document, ? super List<HighlightInfo>> result,
                             @NotNull PsiFile psiFile,
                             @NotNull Document document) {
    Project project = psiFile.getProject();
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    ProcessCanceledException exception = null;
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    // repeat several times when accidental background activity cancels highlighting
    int retries = 100;
    for (int i = 0; i < retries; i++) {
      int oldDelay = settings.getAutoReparseDelay();
      try {
        InspectionProfile currentProfile = myInspectionProfile;
        settings.setAutoReparseDelay(0);
        Function<InspectionProfile, InspectionProfileWrapper> profileProvider =
          p -> currentProfile == null
               ? new InspectionProfileWrapper((InspectionProfileImpl)p)
               : new InspectionProfileWrapper(currentProfile, ((InspectionProfileImpl)p).getProfileManager());
        InspectionProfileWrapper.runWithCustomInspectionWrapper(psiFile, profileProvider, () -> {
          List<HighlightInfo> infos = codeAnalyzer.runMainPasses(psiFile, document, daemonIndicator);
          result.put(document, infos);
        });
        break;
      }
      catch (ProcessCanceledException e) {
        Throwable cause = e.getCause();
        if (cause != null && cause.getClass() != Throwable.class) {
          // canceled because of an exception, no need to repeat the same a lot of times
          throw e;
        }

        exception = e;
      }
      finally {
        settings.setAutoReparseDelay(oldDelay);
      }
    }
    if (exception != null) {
      throw exception;
    }
  }
}
