// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.SearchSession;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class StatusTextAction extends DumbAwareAction implements CustomComponentAction, LightEditCompatible {
  @Override
  public void update(@NotNull AnActionEvent e) {
    SearchSession search = e.getData(SearchSession.KEY);
    JLabel label = (JLabel)e.getPresentation().getClientProperty(COMPONENT_KEY);
    if (label == null) return;
    label.setText(search == null ? "" : search.getComponent().getStatusText());
    label.setForeground(search == null ?
                        ExperimentalUI.isNewUI() ? UIUtil.getLabelInfoForeground() : UIUtil.getLabelForeground() :
                        search.getComponent().getStatusColor());
    e.getPresentation().setEnabled(e.isFromActionToolbar());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JLabel label = new JLabel();
    //noinspection HardCodedStringLiteral
    label.setText(getTextToCountPreferredSize());
    Dimension size = label.getPreferredSize();
    size.height = Math.max(size.height, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height);
    label.setPreferredSize(size);
    label.setText(null);
    label.setHorizontalAlignment(SwingConstants.CENTER);
    return label;
  }

  protected @NotNull @NonNls String getTextToCountPreferredSize() {
    return "9888 results";
  }
}
