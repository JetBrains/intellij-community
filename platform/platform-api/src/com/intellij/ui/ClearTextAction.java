// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.JTextComponent;
import java.awt.*;

public final class ClearTextAction extends AnAction implements DumbAware {
  public ClearTextAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (component instanceof JTextComponent) {
      final JTextComponent textComponent = (JTextComponent)component;
      textComponent.setText("");
    }
  }


  @Override
  public void update(@NotNull AnActionEvent e) {
    final Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (component instanceof JTextComponent) {
      final JTextComponent textComponent = (JTextComponent)component;
      e.getPresentation().setEnabled(textComponent.getText().length() > 0 && ((JTextComponent)component).isEditable());
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}