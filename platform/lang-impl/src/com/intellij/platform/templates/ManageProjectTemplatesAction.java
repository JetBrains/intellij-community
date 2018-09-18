package com.intellij.platform.templates;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class ManageProjectTemplatesAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new ManageProjectTemplatesDialog().show();
  }
}
