// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.SearchSession;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class EditorHeaderToggleAction extends CheckboxAction implements DumbAware, LightEditCompatible {
  protected EditorHeaderToggleAction(@NotNull @NlsContexts.Checkbox String text) {
    this(text, null, null, null);
  }

  protected EditorHeaderToggleAction(@NotNull @NlsContexts.Checkbox String text, @Nullable Icon icon, @Nullable Icon hoveredIcon, @Nullable Icon selectedIcon) {
    super(text);
    getTemplatePresentation().setIcon(icon);
    getTemplatePresentation().setHoveredIcon(hoveredIcon);
    getTemplatePresentation().setSelectedIcon(selectedIcon);
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JComponent customComponent = super.createCustomComponent(presentation, place);
    customComponent.setFocusable(false);
    customComponent.setOpaque(false);
    return customComponent;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    SearchSession search = e.getData(SearchSession.KEY);
    return search != null && isSelected(search);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean selected) {
    SearchSession search = e.getData(SearchSession.KEY);
    if (search != null) {
      setSelected(search, selected);
    }
  }

  protected abstract boolean isSelected(@NotNull SearchSession session);

  protected abstract void setSelected(@NotNull SearchSession session, boolean selected);
}
