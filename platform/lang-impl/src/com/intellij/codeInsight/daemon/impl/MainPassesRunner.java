// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainPassesRunner {
  private static final Logger LOG = Logger.getInstance(MainPassesRunner.class);
  private final Project myProject;
  private final String myTitle;
  private final InspectionProfile myInspectionProfile;

  public MainPassesRunner(@NotNull Project project, @NotNull String title, @Nullable InspectionProfile inspectionProfile) {
    myProject = project;
    myTitle = title;
    myInspectionProfile = inspectionProfile;
  }

  @NotNull
  public Map<Document, List<HighlightInfo>> runMainPasses(@NotNull List<? extends VirtualFile> filesToCheck) {
    Map<Document, List<HighlightInfo>> result = new HashMap<>();
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
            runMainPasses(filesToCheck, result, progress);
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
      runMainPasses(filesToCheck, result, ProgressManager.getInstance().getProgressIndicator());
    }
    else {
      throw new RuntimeException("Must run from Event Dispatch Thread or with a progress indicator");
    }

    return result;
  }

  private void runMainPasses(@NotNull List<? extends VirtualFile> files, @NotNull Map<? super Document, List<HighlightInfo>> result, @NotNull ProgressIndicator progress) {
    for (int i = 0; i < files.size(); i++) {
      ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(progress);

      VirtualFile file = files.get(i);

      progress.setText(ProjectUtil.calcRelativeToProjectPath(file, myProject));
      progress.setFraction((double)i / (double)files.size());

      DaemonProgressIndicator daemonIndicator = new DaemonProgressIndicator();
      ((ProgressIndicatorEx)progress).addStateDelegate(new AbstractProgressIndicatorExBase() {
        @Override
        public void cancel() {
          super.cancel();
          daemonIndicator.cancel();
        }
      });

      runMainPasses(file, result, daemonIndicator);
    }
  }

  private void runMainPasses(@NotNull VirtualFile file,
                             @NotNull Map<? super Document, List<HighlightInfo>> result,
                             @NotNull DaemonProgressIndicator daemonIndicator) {
    PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(file));
    Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
    if (psiFile == null || document == null || !ReadAction.compute(() -> ProblemHighlightFilter.shouldProcessFileInBatch(psiFile))) {
      return;
    }
    ProperTextRange range = ReadAction.compute(() -> ProperTextRange.create(0, document.getTextLength()));
    HighlightingSessionImpl.createHighlightingSession(psiFile, daemonIndicator, null, range, false);
    ProgressManager.getInstance().runProcess(() -> runMainPasses(daemonIndicator, result, psiFile, document), daemonIndicator);
  }

  private void runMainPasses(@NotNull ProgressIndicator daemonIndicator,
                             @NotNull Map<? super Document, List<HighlightInfo>> result,
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
        InspectionProfileWrapper.runWithCustomInspectionWrapper(psiFile, p -> currentProfile == null ? new InspectionProfileWrapper(
          (InspectionProfileImpl)p) : new InspectionProfileWrapper(currentProfile,
                                                                   ((InspectionProfileImpl)p).getProfileManager()), () -> {
          List<HighlightInfo> infos = ReadAction.nonBlocking(() -> codeAnalyzer.runMainPasses(psiFile, document, daemonIndicator)).inSmartMode(project).executeSynchronously();
          result.computeIfAbsent(document, __ -> new ArrayList<>()).addAll(infos);
        });
        break;
      }
      catch (ProcessCanceledException e) {
        Throwable cause = e.getCause();
        if (cause != null && cause.getClass() != Throwable.class) {
          // canceled because of an exception, no need to repeat the same a lot times
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
