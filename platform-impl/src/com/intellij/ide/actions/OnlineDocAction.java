package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;

/**
 * @author Vladimir Kondratyev
 */
public class OnlineDocAction extends AnAction{
  public void actionPerformed(AnActionEvent e) {
    BrowserUtil.launchBrowser(ApplicationInfoImpl.getShadowInstance().getDocumentationUrl());
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(ApplicationInfoImpl.getShadowInstance().getDocumentationUrl() != null);
  }
}
