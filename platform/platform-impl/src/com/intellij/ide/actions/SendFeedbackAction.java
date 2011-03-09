/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import com.intellij.ui.LicensingFacade;

public class SendFeedbackAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    launchBrowser();
  }

  public static void launchBrowser() {
    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    String urlTemplate = appInfo.isEAP() ? appInfo.getEAPFeedbackUrl() : appInfo.getReleaseFeedbackUrl();
    urlTemplate = urlTemplate
      .replace("$BUILD", appInfo.getBuild().asString())
      .replace("$TIMEZONE", System.getProperty("user.timezone"))
      .replace("$EVAL", isEvaluationLicense() ? "true" : "false");
    BrowserUtil.launchBrowser(urlTemplate);
  }

  private static boolean isEvaluationLicense() {
    final LicensingFacade provider = LicensingFacade.getInstance();
    return provider != null && provider.isEvaluationLicense();
  }
}
