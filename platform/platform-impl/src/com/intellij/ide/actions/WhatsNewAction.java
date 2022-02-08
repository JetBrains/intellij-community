// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.Urls;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.intellij.openapi.application.ex.ApplicationInfoEx.WHATS_NEW_AUTO;
import static com.intellij.openapi.application.ex.ApplicationInfoEx.WHATS_NEW_EMBED;

public class WhatsNewAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean available = ApplicationInfoEx.getInstanceEx().getWhatsNewUrl() != null;
    e.getPresentation().setEnabledAndVisible(available);
    if (available) {
      e.getPresentation().setText(IdeBundle.messagePointer("whats.new.action.custom.text", ApplicationNamesInfo.getInstance().getFullProductName()));
      e.getPresentation().setDescription(IdeBundle.messagePointer("whats.new.action.custom.description", ApplicationNamesInfo.getInstance().getFullProductName()));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String whatsNewUrl = ApplicationInfoEx.getInstanceEx().getWhatsNewUrl();
    if (whatsNewUrl == null) throw new IllegalStateException();

    Project project = e.getProject();
    if (project != null && JBCefApp.isSupported() && ApplicationInfoEx.getInstanceEx().isWhatsNewEligibleFor(WHATS_NEW_EMBED)) {
      openWhatsNewPage(project, whatsNewUrl);
    }
    else {
      BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(whatsNewUrl));
    }
  }

  @ApiStatus.Internal
  public static boolean isAvailable() {
    return ApplicationInfoEx.getInstanceEx().isWhatsNewEligibleFor(WHATS_NEW_AUTO) || Boolean.getBoolean("whats.new.notification");
  }

  public static void openWhatsNewPage(@NotNull Project project, @NotNull String url) {
    if (!JBCefApp.isSupported()) {
      String name = ApplicationNamesInfo.getInstance().getFullProductName();
      String version = ApplicationInfo.getInstance().getShortVersion();
      UpdateChecker.getNotificationGroupForIdeUpdateResults()
        .createNotification(IdeBundle.message("whats.new.notification.text", name, version), NotificationType.INFORMATION)
        .setIcon(AllIcons.Nodes.PpWeb)
        .setDisplayId("ide.whats.new")
        .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("whats.new.notification.action"), () -> BrowserUtil.browse(url)))
        .notify(project);
    }
    else {
      boolean darkTheme = UIUtil.isUnderDarcula();

      Map<String, String> parameters = darkTheme ? Map.of("var", "embed", "theme", "dark") : Map.of("var", "embed");
      String embeddedUrl = Urls.newFromEncoded(url).addParameters(parameters).toExternalForm();

      String timeoutContent = null;
      try (InputStream html = WhatsNewAction.class.getResourceAsStream("whatsNewTimeoutText.html")) {
        if (html != null) {
          //noinspection HardCodedStringLiteral
          timeoutContent = new String(StreamUtil.readBytes(html), StandardCharsets.UTF_8)
            .replace("__THEME__", darkTheme ? "theme-dark" : "")
            .replace("__TITLE__", IdeBundle.message("whats.new.timeout.title"))
            .replace("__MESSAGE__", IdeBundle.message("whats.new.timeout.message"))
            .replace("__ACTION__", IdeBundle.message("whats.new.timeout.action", url));
        }
      }
      catch (IOException e) {
        Logger.getInstance(WhatsNewAction.class).error(e);
      }

      String title = IdeBundle.message("update.whats.new", ApplicationNamesInfo.getInstance().getFullProductName());
      HTMLEditorProvider.openEditor(project, title, embeddedUrl, timeoutContent);
    }
  }
}
