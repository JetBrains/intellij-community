package com.intellij.platform.templates;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * @author Dmitry Avdeev
 *         Date: 11/13/12
 */
public class ManageProjectTemplatesAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new ManageProjectTemplatesDialog().show();
  }
}
