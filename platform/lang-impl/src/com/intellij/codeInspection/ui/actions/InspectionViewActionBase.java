// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions;

import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class InspectionViewActionBase extends AnAction {
  public InspectionViewActionBase(@NotNull Supplier<String> text, @NotNull Supplier<String> description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public InspectionViewActionBase(@NotNull Supplier<String> name) {
    super(name);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final InspectionResultsView view = getView(e);
    final boolean enabled = view != null && isEnabled(view, e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
  }

  protected boolean isEnabled(@NotNull InspectionResultsView view, AnActionEvent e) {
    return true;
  }

  @Nullable
  public static InspectionResultsView getView(@Nullable AnActionEvent event) {
    if (event == null) {
      return null;
    }
    InspectionResultsView view = event.getData(InspectionResultsView.DATA_KEY);
    if (view == null) {
      Project project = event.getProject();
      if (project == null) return null;
      ToolWindowManager twManager = ToolWindowManager.getInstance(project);
      ToolWindow window = twManager.getToolWindow(ProblemsView.ID);
      if (window == null) {
        return null;
      }
      ContentManager contentManager = window.getContentManagerIfCreated();
      Content selectedContent = contentManager != null ? contentManager.getSelectedContent() : null;
      if (selectedContent == null) return null;
      DataContext twContext = DataManager.getInstance().getDataContext(selectedContent.getComponent());
      view = InspectionResultsView.DATA_KEY.getData(twContext);
      if (view == null) return null;
    }
    return view.isDisposed() ? null : view;
  }
}
