// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.FeedbackDescriptionProvider;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SendFeedbackAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ApplicationInfoEx info = ApplicationInfoEx.getInstanceEx();
    boolean isSupportedOS = SystemInfo.isMac || SystemInfo.isLinux || SystemInfo.isWindows;
    if (info != null && info.getFeedbackUrl() != null && isSupportedOS) {
      String feedbackSite = getFeedbackHost(info.getFeedbackUrl(), info.getCompanyName());
      e.getPresentation().setDescription(ActionsBundle.message("action.SendFeedback.detailed.description", feedbackSite));
      e.getPresentation().setEnabledAndVisible(true);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  private static String getFeedbackHost(String feedbackUrl, String companyName) {
    Pattern uriPattern = Pattern.compile("[^:/?#]+://(?:www\\.)?([^/?#]*).*", Pattern.DOTALL);
    Matcher matcher = uriPattern.matcher(feedbackUrl);
    return matcher.matches() ? matcher.group(1) : companyName;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    doPerformAction(e.getProject());
  }

  public static void doPerformAction(@Nullable Project project) {
    doPerformActionImpl(project, ApplicationInfoEx.getInstanceEx().getFeedbackUrl(), getDescription(project));
  }

  public static void doPerformAction(@Nullable Project project, @NotNull String description) {
    doPerformActionImpl(project, ApplicationInfoEx.getInstanceEx().getFeedbackUrl(), description);
  }

  static void doPerformActionImpl(@Nullable Project project,
                                  @NotNull String urlTemplate,
                                  @NotNull String description) {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    boolean eap = appInfo.isEAP();
    LicensingFacade la = LicensingFacade.getInstance();
    String url = urlTemplate
      .replace("$BUILD", eap ? appInfo.getBuild().asStringWithoutProductCode() : appInfo.getBuild().asString())
      .replace("$TIMEZONE", System.getProperty("user.timezone"))
      .replace("$VERSION", appInfo.getFullVersion())
      .replace("$EVAL", la != null && la.isEvaluationLicense() ? "true" : "false")
      .replace("$DESCR", description);
    BrowserUtil.browse(url, project);
  }

  /**
   * @deprecated use {@link #getDescription(Project)} instead
   */
  @Deprecated
  @NotNull
  public static String getDescription() {
    return getDescription(null);
  }

  @NotNull
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
      if (osPatchLevel != null && !"unknown".equals(osPatchLevel)) {
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
      if (UIUtil.isRetina()) {
        sb.append(SystemInfo.isMac ? "; Retina" : "; HiDPI");
      }
    }
    for (FeedbackDescriptionProvider ext : EP_NAME.getExtensions()) {
      String pluginDescription = ext.getDescription(project);
      if (pluginDescription != null && pluginDescription.length() > 0) {
        sb.append("\n").append(pluginDescription);
      }
    }
    return sb.toString();
  }

  private static final ExtensionPointName<FeedbackDescriptionProvider> EP_NAME = new ExtensionPointName<>("com.intellij.feedbackDescriptionProvider");
}