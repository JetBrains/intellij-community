// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependenciesToolWindow;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class DependenciesHandlerBase {
  @NotNull
  protected final Project myProject;
  private final List<? extends AnalysisScope> myScopes;
  private final Set<PsiFile> myExcluded;

  public DependenciesHandlerBase(@NotNull Project project, final List<? extends AnalysisScope> scopes, Set<PsiFile> excluded) {
    myScopes = scopes;
    myExcluded = excluded;
    myProject = project;
  }

  public void analyze() {
    final List<DependenciesBuilder> builders = new ArrayList<>();

    final Task task;
    if (canStartInBackground()) {
      task = new Task.Backgroundable(myProject, getProgressTitle(), true, new PerformAnalysisInBackgroundOption(myProject)) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          indicator.setIndeterminate(false);
          perform(builders, indicator);
        }

        @Override
        public void onSuccess() {
          DependenciesHandlerBase.this.onSuccess(builders);
        }
      };
    } else {
      task = new Task.Modal(myProject, getProgressTitle(), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(false);
          perform(builders, indicator);
        }

        @Override
        public void onSuccess() {
          DependenciesHandlerBase.this.onSuccess(builders);
        }
      };
    }
    ProgressManager.getInstance().run(task);
  }

  protected boolean canStartInBackground() {
    return true;
  }

  protected boolean shouldShowDependenciesPanel(List<? extends DependenciesBuilder> builders) {
    return true;
  }

  protected abstract @NlsContexts.ProgressTitle String getProgressTitle();

  protected abstract @NlsContexts.TabTitle String getPanelDisplayName(AnalysisScope scope);

  protected abstract DependenciesBuilder createDependenciesBuilder(AnalysisScope scope);

  private void perform(List<DependenciesBuilder> builders, @NotNull ProgressIndicator indicator) {
    try {
      PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
      for (AnalysisScope scope : myScopes) {
        builders.add(createDependenciesBuilder(scope));
      }
      for (DependenciesBuilder builder : builders) {
        builder.analyze();
      }
      snapshot.logResponsivenessSinceCreation("Dependency analysis");
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(myProject).showDumbModeNotification(
        CodeInsightBundle.message("analyze.dependencies.not.available.notification.indexing"));
      throw new ProcessCanceledException();
    }
  }

  private void onSuccess(final List<DependenciesBuilder> builders) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      if (shouldShowDependenciesPanel(builders)) {
        final String displayName = getPanelDisplayName(builders);
        DependenciesPanel panel = new DependenciesPanel(myProject, builders, myExcluded);
        Content content = ContentFactory.getInstance().createContent(panel, displayName, false);
        content.setDisposer(panel);
        panel.setContent(content);
        DependenciesToolWindow.getInstance(myProject).addContent(content);
      }
    });
  }

  protected @NlsContexts.TabTitle String getPanelDisplayName(List<? extends DependenciesBuilder> builders) {
    return getPanelDisplayName(builders.get(0).getScope());
  }
}
