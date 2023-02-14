// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cyclicDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.cyclicDependencies.ui.CyclicDependenciesPanel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependenciesToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CyclicDependenciesHandler {
  @NotNull
  private final Project myProject;
  private final AnalysisScope myScope;

  public CyclicDependenciesHandler(@NotNull Project project, @NotNull AnalysisScope scope) {
    myProject = project;
    myScope = scope;
  }

  public void analyze() {
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject, myScope);
    final Runnable successRunnable = () -> SwingUtilities.invokeLater(() -> {
      CyclicDependenciesPanel panel = new CyclicDependenciesPanel(myProject, builder);
      Content content = ContentFactory.getInstance().createContent(panel, JavaBundle.message(
        "action.analyzing.cyclic.dependencies.in.scope", builder.getScope().getDisplayName()), false);
      content.setDisposer(panel);
      panel.setContent(content);
      DependenciesToolWindow.getInstance(myProject).addContent(content);
    });
    ProgressManager.getInstance()
      .runProcessWithProgressAsynchronously(myProject, CodeInsightBundle.message("package.dependencies.progress.title"),
                                            () -> builder.analyze(), successRunnable, null, new PerformAnalysisInBackgroundOption(myProject));
  }
}
