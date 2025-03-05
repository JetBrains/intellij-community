// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.packaging.ui.TreeNodePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ArtifactEditorNavigateActionBase extends DumbAwareAction {
  public ArtifactEditorNavigateActionBase(JComponent contextComponent) {
    super(JavaUiBundle.message("action.name.facet.navigate"));
    registerCustomShortcutSet(CommonShortcuts.getEditSource(), contextComponent);
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    final TreeNodePresentation presentation = getPresentation();
    e.getPresentation().setEnabled(presentation != null && presentation.canNavigateToSource());
  }

  protected abstract @Nullable TreeNodePresentation getPresentation();

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final TreeNodePresentation presentation = getPresentation();
    if (presentation != null) {
      presentation.navigateToSource();
    }
  }
}
