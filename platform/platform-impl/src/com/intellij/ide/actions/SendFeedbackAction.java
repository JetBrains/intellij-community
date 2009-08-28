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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.LicenseeInfoProvider;

public class SendFeedbackAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    launchBrowser();
  }

  public static void launchBrowser() {
    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    String urlTemplate = appInfo.isEAP() ? appInfo.getEAPFeedbackUrl() : appInfo.getReleaseFeedbackUrl();
    urlTemplate = urlTemplate
      .replace("$BUILD", appInfo.getBuildNumber())
      .replace("$TIMEZONE", System.getProperty("user.timezone"))
      .replace("$EVAL", isEvaluationLicense() ? "true" : "false");
    BrowserUtil.launchBrowser(urlTemplate);
  }

  private static boolean isEvaluationLicense() {
    final LicenseeInfoProvider provider = LicenseeInfoProvider.getInstance();
    return provider != null && provider.isEvaluationLicense();
  }
}
