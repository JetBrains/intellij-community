// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.AppMode;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
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
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.Urls;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WhatsNewAction extends AnAction implements DumbAware {
  private static final String ENABLE_NEW_UI_REQUEST = "enable-new-UI";

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
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String whatsNewUrl = ApplicationInfoEx.getInstanceEx().getWhatsNewUrl();
    if (whatsNewUrl == null) throw new IllegalStateException();

    Project project = e.getProject();
    if (project != null && JBCefApp.isSupported()) {
      openWhatsNewPage(project, whatsNewUrl);
    }
    else {
      BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(whatsNewUrl));
    }
  }

  @ApiStatus.Internal
  public static boolean isAvailable() {
    return ApplicationInfoEx.getInstanceEx().isShowWhatsNewOnUpdate() && !AppMode.isRemoteDevHost();
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

      Map<String, String> parameters = new HashMap<>();
      parameters.put("var", "embed");
      if (darkTheme) {
        parameters.put("theme", "dark");
      }
      Locale locale = Locale.getDefault();
      if (locale != null) {
        parameters.put("lang", locale.toLanguageTag().toLowerCase(Locale.ENGLISH));
      }
      var request = HTMLEditorProvider.Request.url(Urls.newFromEncoded(url).addParameters(parameters).toExternalForm());

      try (var stream = WhatsNewAction.class.getResourceAsStream("whatsNewTimeoutText.html")) {
        if (stream != null) {
          request.withTimeoutHtml(new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                                    .replace("__THEME__", darkTheme ? "theme-dark" : "")
                                    .replace("__TITLE__", IdeBundle.message("whats.new.timeout.title"))
                                    .replace("__MESSAGE__", IdeBundle.message("whats.new.timeout.message"))
                                    .replace("__ACTION__", IdeBundle.message("whats.new.timeout.action", url)));
        }
      }
      catch (IOException e) {
        Logger.getInstance(WhatsNewAction.class).error(e);
      }

      request.withQueryHandler((HTMLEditorProvider.JsQueryHandler.Java)(id, jsRequest) -> {
        if (ENABLE_NEW_UI_REQUEST.equals(jsRequest)) {
          //todo[KB] please put the implementation here
          return "true";
        }
        else {
          throw new IllegalArgumentException("Unexpected query: " + jsRequest);
        }
      });

      var title = IdeBundle.message("update.whats.new", ApplicationNamesInfo.getInstance().getFullProductName());
      HTMLEditorProvider.openEditor(project, title, request);
    }
  }
}
