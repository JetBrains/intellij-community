// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
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

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Nullable
  public static InspectionResultsView getView(@Nullable AnActionEvent event) {
    if (event == null) {
      return null;
    }
    InspectionResultsView view = event.getData(InspectionResultsView.DATA_KEY);
    return view != null && view.isDisposed() ? null : view;
  }
}
