// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependenciesToolWindow;
import com.intellij.packageDependencies.DependencyAnalysisResult;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Set;

public abstract class DependenciesHandlerBase {
  protected final @NotNull Project myProject;
  private final List<? extends AnalysisScope> myScopes;
  private final Set<PsiFile> myExcluded;

  public DependenciesHandlerBase(@NotNull Project project, final List<? extends AnalysisScope> scopes, Set<PsiFile> excluded) {
    myScopes = scopes;
    myExcluded = excluded;
    myProject = project;
  }

  public void analyze() {
    final DependencyAnalysisResult result = createAnalysisResult();

    final Task task;
    if (canStartInBackground()) {
      task = new Task.Backgroundable(myProject, getProgressTitle(), true, new PerformAnalysisInBackgroundOption(myProject)) {
        @Override
        public void run(final @NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(false);
          perform(result, indicator);
        }

        @Override
        public void onSuccess() {
          DependenciesHandlerBase.this.onSuccess(result);
        }
      };
    } else {
      task = new Task.Modal(myProject, getProgressTitle(), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(false);
          perform(result, indicator);
        }

        @Override
        public void onSuccess() {
          DependenciesHandlerBase.this.onSuccess(result);
        }
      };
    }
    ProgressManager.getInstance().run(task);
  }

  protected @NotNull DependencyAnalysisResult createAnalysisResult() {
    return new DependencyAnalysisResult();
  }

  protected boolean canStartInBackground() {
    return true;
  }

  protected boolean shouldShowDependenciesPanel(@NotNull DependencyAnalysisResult result) {
    return true;
  }

  protected abstract @NlsContexts.ProgressTitle String getProgressTitle();

  protected abstract @NlsContexts.TabTitle String getPanelDisplayName(AnalysisScope scope);

  protected abstract DependenciesBuilder createDependenciesBuilder(AnalysisScope scope);

  private void perform(DependencyAnalysisResult result, @NotNull ProgressIndicator indicator) {
    try {
      PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
      for (AnalysisScope scope : myScopes) {
        result.addBuilder(createDependenciesBuilder(scope));
      }
      for (DependenciesBuilder builder : result.getBuilders()) {
        builder.analyze();
      }
      bgtPostAnalyze(result);
      snapshot.logResponsivenessSinceCreation("Dependency analysis");
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(myProject).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("analyze.dependencies.not.available.notification.indexing"), DumbModeBlockedFunctionality.PackageDependencies);
      throw new ProcessCanceledException();
    }
  }

  protected void bgtPostAnalyze(DependencyAnalysisResult result) {
    result.panelDisplayName = ReadAction.compute(() -> getPanelDisplayName(result.getBuilders().get(0).getScope()));
  }

  private void onSuccess(final DependencyAnalysisResult result) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      if (shouldShowDependenciesPanel(result)) {
        final String displayName = result.getPanelDisplayName();
        DependenciesPanel panel = new DependenciesPanel(myProject, result.getBuilders(), myExcluded);
        Content content = ContentFactory.getInstance().createContent(panel, displayName, false);
        content.setDisposer(panel);
        panel.setContent(content);
        DependenciesToolWindow.getInstance(myProject).addContent(content);
      }
    });
  }

}
