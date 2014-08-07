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
import com.intellij.openapi.project.Project;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class SendFeedbackAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    launchBrowser(e.getProject());
  }

  public static void launchBrowser(@Nullable Project project) {
    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    boolean eap = appInfo.isEAP();
    String urlTemplate = eap ? appInfo.getEAPFeedbackUrl() : appInfo.getReleaseFeedbackUrl();
    urlTemplate = urlTemplate
      .replace("$BUILD", eap ? appInfo.getBuild().asStringWithoutProductCode() : appInfo.getBuild().asString())
      .replace("$TIMEZONE", System.getProperty("user.timezone"))
      .replace("$EVAL", isEvaluationLicense() ? "true" : "false")
      .replace("$DESCR", getDescription());
    BrowserUtil.browse(urlTemplate, project);
  }

  private static String getDescription() {
    StringBuilder sb = new StringBuilder("\n\n");
    String javaVersion = System.getProperty("java.runtime.version", System.getProperty("java.version", "unknown"));
    sb.append(javaVersion);
    String archDataModel = System.getProperty("sun.arch.data.model");
    if (archDataModel != null) {
      sb.append("x").append(archDataModel);
    }
    String javaVendor = System.getProperty("java.vm.vendor");
    if (javaVendor != null) {
      sb.append(" ").append(javaVendor);
    }
    sb.append(", ").append(System.getProperty("os.name"));
    String osArch = System.getProperty("os.arch");
    if (osArch != null) {
      sb.append("(").append(osArch).append(")");
    }

    String osVersion = System.getProperty("os.version");
    String osPatchLevel = System.getProperty("sun.os.patch.level");
    if (osVersion != null) {
      sb.append(" v").append(osVersion);
      if (osPatchLevel != null) {
        sb.append(" ").append(osPatchLevel);
      }
    }
    if (!GraphicsEnvironment.isHeadless()) {
      GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
      sb.append(" (");
      for (int i = 0; i < devices.length; i++) {
        if (i > 0) sb.append(", ");
        GraphicsDevice device = devices[i];
        Rectangle bounds = device.getDefaultConfiguration().getBounds();
        sb.append(bounds.width).append("x").append(bounds.height);
      }
      if (UIUtil.isRetina()) sb.append(" R");
      sb.append(")");
    }
    return sb.toString();
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(ApplicationInfoEx.getInstanceEx() != null);
  }

  private static boolean isEvaluationLicense() {
    final LicensingFacade provider = LicensingFacade.getInstance();
    return provider != null && provider.isEvaluationLicense();
  }
}
