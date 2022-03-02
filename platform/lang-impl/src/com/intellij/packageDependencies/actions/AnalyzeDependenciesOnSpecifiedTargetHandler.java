// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyVisitorFactory;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AnalyzeDependenciesOnSpecifiedTargetHandler extends DependenciesHandlerBase {
  private static final NotificationGroup NOTIFICATION_GROUP =
    NotificationGroupManager.getInstance().getNotificationGroup("Dependencies");
  private final GlobalSearchScope myTargetScope;

  public AnalyzeDependenciesOnSpecifiedTargetHandler(@NotNull Project project, @NotNull AnalysisScope scope, @NotNull GlobalSearchScope targetScope) {
    super(project, Collections.singletonList(scope), new HashSet<>());
    myTargetScope = targetScope;
  }

  @Override
  protected String getProgressTitle() {
    return CodeInsightBundle.message("package.dependencies.progress.title");
  }

  @Override
  protected String getPanelDisplayName(AnalysisScope scope) {
    return CodeInsightBundle.message("package.dependencies.on.toolwindow.title", scope.getDisplayName(), myTargetScope.getDisplayName());
  }

  @Override
  protected boolean shouldShowDependenciesPanel(List<? extends DependenciesBuilder> builders) {
    for (DependenciesBuilder builder : builders) {
      for (Set<PsiFile> files : builder.getDependencies().values()) {
        if (!files.isEmpty()) {
          return true;
        }
      }
    }
    final String source = StringUtil.decapitalize(getPanelDisplayName(builders));
    final String target = StringUtil.decapitalize(myTargetScope.getDisplayName());
    String message = CodeInsightBundle.message("no.dependencies.found.message", source, target);
    if (DependencyVisitorFactory.VisitorOptions.fromSettings(myProject).skipImports()) {
      message += " ";
      message += CodeInsightBundle.message("dependencies.in.imports.message");
    }
    NOTIFICATION_GROUP.createNotification(message, MessageType.INFO).notify(myProject);
    return false;
  }

  @Override
  protected DependenciesBuilder createDependenciesBuilder(AnalysisScope scope) {
    return new ForwardDependenciesBuilder(myProject, scope, myTargetScope);
  }
}
