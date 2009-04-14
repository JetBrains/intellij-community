/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 26.09.2006
 * Time: 19:33:19
 */
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.DumbAware;

public class TechnicalSupportAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    BrowserUtil.launchBrowser(ApplicationInfoImpl.getShadowInstance().getSupportUrl());
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(ApplicationInfoImpl.getShadowInstance().getSupportUrl() != null);
  }
}