/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      Content content = ContentFactory.SERVICE.getInstance().createContent(panel, JavaBundle.message(
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
