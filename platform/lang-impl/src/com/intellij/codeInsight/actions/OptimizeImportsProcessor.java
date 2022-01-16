// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInspection.HintAction;
import com.intellij.formatting.service.FormattingService;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.HintText;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.CoreCodeStyleUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.FutureTask;

import static com.intellij.codeInsight.actions.OptimizeImportsProcessor.NotificationInfo.NOTHING_CHANGED_NOTIFICATION;
import static com.intellij.codeInsight.actions.OptimizeImportsProcessor.NotificationInfo.SOMETHING_CHANGED_WITHOUT_MESSAGE_NOTIFICATION;

public class OptimizeImportsProcessor extends AbstractLayoutCodeProcessor {
  private final List<NotificationInfo> myOptimizerNotifications = new SmartList<>();

  public OptimizeImportsProcessor(@NotNull Project project) {
    super(project, getCommandName(), getProgressText(), false);
  }

  public OptimizeImportsProcessor(@NotNull Project project, @NotNull Module module) {
    super(project, module, getCommandName(), getProgressText(), false);
  }

  public OptimizeImportsProcessor(@NotNull Project project,
                                  @NotNull PsiDirectory directory,
                                  boolean includeSubdirs,
                                  boolean processOnlyVcsChangedFiles) {
    super(project, directory, includeSubdirs, getProgressText(), getCommandName(), processOnlyVcsChangedFiles);
  }

  public OptimizeImportsProcessor(@NotNull Project project, @NotNull PsiFile file) {
    super(project, file, getProgressText(), getCommandName(), false);
  }

  public OptimizeImportsProcessor(@NotNull Project project, PsiFile @NotNull [] files, @Nullable Runnable postRunnable) {
    this(project, files, getCommandName(), postRunnable);
  }

  public OptimizeImportsProcessor(@NotNull Project project,
                                  PsiFile @NotNull [] files,
                                  @NotNull @NlsContexts.Command String commandName,
                                  @Nullable Runnable postRunnable) {
    super(project, files, getProgressText(), commandName, postRunnable, false);
  }

  public OptimizeImportsProcessor(@NotNull AbstractLayoutCodeProcessor previousProcessor) {
    super(previousProcessor, getCommandName(), getProgressText());
  }

  @Override
  @NotNull
  protected FutureTask<Boolean> prepareTask(@NotNull PsiFile file, boolean processChangedTextOnly) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (DumbService.isDumb(file.getProject())) {
      return emptyTask();
    }

    List<Runnable> runnables = collectOptimizers(file);

    if (runnables.isEmpty()) {
      return emptyTask();
    }

    List<HintAction> hints = ApplicationManager.getApplication().isDispatchThread() ?
                             Collections.emptyList() : ShowAutoImportPass.getImportHints(file);

    return new FutureTask<>(() -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      CoreCodeStyleUtil.setSequentialProcessingAllowed(false);
      try {
        for (Runnable runnable : runnables) {
          runnable.run();
          myOptimizerNotifications.add(getNotificationInfo(runnable));
        }
        putNotificationInfoIntoCollector();
        ShowAutoImportPass.fixAllImportsSilently(file, hints);
      }
      finally {
        CoreCodeStyleUtil.setSequentialProcessingAllowed(true);
      }
    }, true);
  }

  static @NotNull List<Runnable> collectOptimizers(@NotNull PsiFile file) {
    FormattingService service = FormattingServiceUtil.findImportsOptimizingService(file);
    List<Runnable> runnables = new ArrayList<>();
    List<PsiFile> files = file.getViewProvider().getAllFiles();
    for (ImportOptimizer optimizer : service.getImportOptimizers(file)) {
      for (PsiFile psiFile : files) {
        if (optimizer.supports(psiFile)) {
          runnables.add(optimizer.processFile(psiFile));
        }
      }
    }
    return runnables;
  }

  @NotNull
  private static NotificationInfo getNotificationInfo(@NotNull Runnable runnable) {
    if (runnable instanceof ImportOptimizer.CollectingInfoRunnable) {
      String optimizerMessage = ((ImportOptimizer.CollectingInfoRunnable)runnable).getUserNotificationInfo();
      return optimizerMessage == null ? NOTHING_CHANGED_NOTIFICATION : new NotificationInfo(optimizerMessage);
    }
    if (runnable == EmptyRunnable.getInstance()) {
      return NOTHING_CHANGED_NOTIFICATION;
    }
    return SOMETHING_CHANGED_WITHOUT_MESSAGE_NOTIFICATION;
  }

  private void putNotificationInfoIntoCollector() {
    LayoutCodeInfoCollector collector = getInfoCollector();
    if (collector == null) {
      return;
    }

    boolean atLeastOneOptimizerChangedSomething = false;
    for (NotificationInfo info : myOptimizerNotifications) {
      atLeastOneOptimizerChangedSomething |= info.isSomethingChanged();
      if (info.getMessage() != null) {
        collector.setOptimizeImportsNotification(info.getMessage());
        return;
      }
    }

    String hint = atLeastOneOptimizerChangedSomething ? CodeInsightBundle.message("hint.text.imports.optimized") : null;
    collector.setOptimizeImportsNotification(hint);
  }

  static class NotificationInfo {
    static final NotificationInfo NOTHING_CHANGED_NOTIFICATION = new NotificationInfo(false, null);
    static final NotificationInfo SOMETHING_CHANGED_WITHOUT_MESSAGE_NOTIFICATION = new NotificationInfo(true, null);

    private final boolean mySomethingChanged;
    @HintText
    private final String myMessage;

    NotificationInfo(@NotNull @HintText String message) {
      this(true, message);
    }

    public boolean isSomethingChanged() {
      return mySomethingChanged;
    }

    public @HintText String getMessage() {
      return myMessage;
    }

    private NotificationInfo(boolean isSomethingChanged, @Nullable @HintText String message) {
      mySomethingChanged = isSomethingChanged;
      myMessage = message;
    }
  }

  private static @NotNull @NlsContexts.ProgressText String getProgressText() {
    return CodeInsightBundle.message("progress.text.optimizing.imports");
  }

  public static @NotNull @NlsContexts.Command String getCommandName() {
    return CodeInsightBundle.message("process.optimize.imports");
  }
}
