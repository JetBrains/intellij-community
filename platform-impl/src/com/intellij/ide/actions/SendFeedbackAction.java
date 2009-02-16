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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.license.LicenseManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;

public class SendFeedbackAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    launchBrowser();
  }

  public static void launchBrowser() {
    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    String urlTemplate = LicenseManager.getInstance().isEap() ? appInfo.getEAPFeedbackUrl() : appInfo.getReleaseFeedbackUrl();
    urlTemplate = urlTemplate
      .replace("$BUILD", appInfo.getBuildNumber())
      .replace("$TIMEZONE", System.getProperty("user.timezone"))
      .replace("$EVAL", LicenseManager.getInstance().isEvaluationLicense() ? "true" : "false");
    BrowserUtil.launchBrowser(urlTemplate);
  }
}
