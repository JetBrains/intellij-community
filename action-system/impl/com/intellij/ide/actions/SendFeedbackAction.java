/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 25.07.2006
 * Time: 14:26:00
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ide.BrowserUtil;

public class SendFeedbackAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    BrowserUtil.launchBrowser("http://www.jetbrains.com/idea/feedback/");
  }
}
