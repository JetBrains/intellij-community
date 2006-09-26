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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ide.BrowserUtil;

public class TechnicalSupportAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    BrowserUtil.launchBrowser("http://www.jetbrains.com/support/idea/index.html");
  }
}