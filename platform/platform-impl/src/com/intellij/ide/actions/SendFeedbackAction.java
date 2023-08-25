// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.FeedbackDescriptionProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import com.intellij.platform.ide.customization.FeedbackReporter;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class SendFeedbackAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isSupportedOS = SystemInfo.isMac || SystemInfo.isLinux || SystemInfo.isWindows;
    FeedbackReporter feedbackReporter = ExternalProductResourceUrls.getInstance().getFeedbackReporter();
    if (feedbackReporter != null && isSupportedOS) {
      String feedbackSite = feedbackReporter.getDestinationDescription();
      e.getPresentation().setDescription(ActionsBundle.messagePointer("action.SendFeedback.detailed.description", feedbackSite));
      e.getPresentation().setEnabledAndVisible(true);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FeedbackReporter feedbackReporter = ExternalProductResourceUrls.getInstance().getFeedbackReporter();
    if (feedbackReporter != null) {
      boolean formShown = feedbackReporter.showFeedbackForm(e.getProject(), false);
      if (!formShown) {
        submit(e.getProject());
      }
    }
  }

  public static void submit(@Nullable Project project) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, IdeBundle.message("reportProblemAction.progress.title.submitting")) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        openFeedbackPageInBrowser(project, getDescription(project));
      }
    });
  }

  /**
   * @deprecated use {@link FeedbackDescriptionProvider} extension point to provide additional data to description used by the default
   * 'Send Feedback' action instead of implementing your own action and calling this method.
   */
  @Deprecated(forRemoval = true)
  public static void submit(@Nullable Project project, @NotNull String description) {
    openFeedbackPageInBrowser(project, description);
  }

  private static void openFeedbackPageInBrowser(@Nullable Project project, @NotNull String description) {
    FeedbackReporter feedbackReporter = ExternalProductResourceUrls.getInstance().getFeedbackReporter();
    if (feedbackReporter == null) return;
    BrowserUtil.browse(feedbackReporter.feedbackFormUrl(description).toExternalForm(), project);
  }

  /**
   * @deprecated use {@link FeedbackDescriptionProvider} extension point to provide additional data to description used by the default 
   * 'Send Feedback' action instead of implementing your own action and calling this method.
   */
  @Deprecated
  public static void submit(@Nullable Project project, @NotNull String urlTemplate, @NotNull String description) {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    boolean eap = appInfo.isEAP();
    LicensingFacade la = LicensingFacade.getInstance();
    String url = urlTemplate
      .replace("$BUILD", URLUtil.encodeURIComponent(eap ? appInfo.getBuild().asStringWithoutProductCode() : appInfo.getBuild().asString()))
      .replace("$TIMEZONE", URLUtil.encodeURIComponent(System.getProperty("user.timezone", "")))
      .replace("$VERSION", URLUtil.encodeURIComponent(appInfo.getFullVersion()))
      .replace("$EVAL", URLUtil.encodeURIComponent(la != null && la.isEvaluationLicense() ? "true" : "false"))
      .replace("$DESCR", URLUtil.encodeURIComponent(description));
    BrowserUtil.browse(url, project);
  }

  public static @NotNull String getDescription(@Nullable Project project) {
    @NonNls StringBuilder sb = new StringBuilder("\n\n");
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
        DisplayMode displayMode = device.getDisplayMode();
        float scale = JBUIScale.sysScale(device.getDefaultConfiguration());
        sb.append(displayMode.getWidth() * scale).append("x").append(displayMode.getHeight() * scale);
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