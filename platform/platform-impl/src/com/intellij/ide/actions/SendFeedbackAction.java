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

package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.VersionDetailsProvider;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

public class SendFeedbackAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(SendFeedbackAction.class);

  // This format string should be conscious of the fact that a concatenation of such key-value pairs
  // is passed along as a GET parameter in the URL when opening browser, so its length is not unlimited.
  // Hence each of these key-value pairs should be truncated if it exceeds a certain predefined length.
  private static final String VERSION_KEY_VALUE_FORMAT = "%3.8s: %3.32s";
  private static final String VERSION_KEY_VALUE_PAIRS_SEPARATOR = "; ";
  // This may vary between browsers, but it's safe to assume 2048 bytes is an acceptable maximal length of a URL.
  // Setting aside some 256 bytes for the platform, system and JDK information, let's leave 1792 bytes for description
  // provided by the extensions.
  // All of these numbers are largely provisional - the only browser where it may matter indeed is IE, and its usage
  // is declining. So the considerations are not so much around browsers compatibility but around common sense
  // and how large we want the bug metadata to be.
  private static final int MAX_DESCRIPTION_LENGTH_BYTES = 1792;

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
      .replace("$DESCR", getDescription(project));
    BrowserUtil.browse(urlTemplate, project);
  }

  public static String getDescription(@Nullable Project project) {
    StringBuilder sb = new StringBuilder("\n\n");
    sb.append(ApplicationInfoEx.getInstanceEx().getBuild().asString()).append(", ");
    String javaVersion = System.getProperty("java.runtime.version", System.getProperty("java.version", "unknown"));
    sb.append("JRE ");
    sb.append(javaVersion);
    String archDataModel = System.getProperty("sun.arch.data.model");
    if (archDataModel != null) {
      sb.append("x").append(archDataModel);
    }
    String javaVendor = System.getProperty("java.vm.vendor");
    if (javaVendor != null) {
      sb.append(" ").append(javaVendor);
    }
    sb.append(", OS ").append(System.getProperty("os.name"));
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
      sb.append(", screens ");
      GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
      for (int i = 0; i < devices.length; i++) {
        if (i > 0) sb.append(", ");
        GraphicsDevice device = devices[i];
        Rectangle bounds = device.getDefaultConfiguration().getBounds();
        sb.append(bounds.width).append("x").append(bounds.height);
      }
      if (UIUtil.isRetina()) sb.append(SystemInfo.isMac ? "; Retina" : "; HiDPI");
    }

    sb.append("\n\n");
    sb.append(getVersionDetailsFromExtensions(project));

    return sb.toString();
  }

  @NotNull
  private static String getVersionDetailsFromExtensions(@Nullable Project project) {
    StringBuilder sb = new StringBuilder(MAX_DESCRIPTION_LENGTH_BYTES);
    for (VersionDetailsProvider versionDetailsProvider : VersionDetailsProvider.EP_NAME.getExtensions()) {
      if (sb.length() > MAX_DESCRIPTION_LENGTH_BYTES) {
        break;
      }
      try {
        Map<String, String> versionDetails = versionDetailsProvider.getVersionDetails(project);
        for (Map.Entry<String, String> entry : versionDetails.entrySet()) {
          sb.append(String.format(VERSION_KEY_VALUE_FORMAT, entry.getKey(), entry.getValue()));
          sb.append(VERSION_KEY_VALUE_PAIRS_SEPARATOR);
        }
      }
      catch (Throwable e) {
        LOG.info("Exception while calling one of the version details providers", e);
      }
    }

    return sb.substring(0, Math.min(MAX_DESCRIPTION_LENGTH_BYTES, sb.length()));
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
