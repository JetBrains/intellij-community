// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.experimental.ExperimentalUiCollector;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.Urls;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;

public final class WhatsNewAction extends AnAction implements DumbAware {
  private static final String ENABLE_NEW_UI_REQUEST = "enable-new-UI";

  @Override
  public void update(@NotNull AnActionEvent e) {
    var available = ExternalProductResourceUrls.getInstance().getWhatIsNewPageUrl() != null;
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
    var whatsNewUrl = ExternalProductResourceUrls.getInstance().getWhatIsNewPageUrl();
    if (whatsNewUrl == null) throw new IllegalStateException();
    var url = whatsNewUrl.toExternalForm();

    if (ApplicationManager.getApplication().isInternal() && (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) {
      var title = IdeBundle.message("whats.new.action.custom.text", ApplicationNamesInfo.getInstance().getFullProductName());
      var prompt = IdeBundle.message("browser.url.popup");
      url = Messages.showInputDialog(e.getProject(), prompt, title, null, url, null);
      if (url == null) return;
    }

    var project = e.getProject();
    if (project != null && JBCefApp.isSupported()) {
      openWhatsNewPage(project, url);
    }
    else {
      BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url));
    }
  }

  @ApiStatus.Internal
  public static void openWhatsNewPage(@NotNull Project project, @NotNull String url) {
    if (!JBCefApp.isSupported()) {
      var name = ApplicationNamesInfo.getInstance().getFullProductName();
      var version = ApplicationInfo.getInstance().getShortVersion();
      UpdateChecker.getNotificationGroupForIdeUpdateResults()
        .createNotification(IdeBundle.message("whats.new.notification.text", name, version), NotificationType.INFORMATION)
        .setIcon(AllIcons.Nodes.PpWeb)
        .setDisplayId("ide.whats.new")
        .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("whats.new.notification.action"), () -> BrowserUtil.browse(url)))
        .notify(project);
    }
    else {
      openWhatsNewPage(project, url, (id, jsRequest, completion) -> {
        if (ENABLE_NEW_UI_REQUEST.equals(jsRequest)) {
          if (!ExperimentalUI.isNewUI()) {
            ApplicationManager.getApplication().invokeLater(() -> {
              ExperimentalUiCollector.logSwitchUi(ExperimentalUiCollector.SwitchSource.WHATS_NEW_PAGE, true);
              ExperimentalUI.Companion.setNewUI(true);
              UISettings.getInstance().fireUISettingsChanged();
            });
          }
          return "true";
        }
        else {
          throw new IllegalArgumentException("Unexpected query: " + jsRequest);
        }
      });
    }
  }

  @ApiStatus.Internal
  public static void openWhatsNewPage(@NotNull Project project, @NotNull String url, @Nullable HTMLEditorProvider.JsQueryHandler queryHandler) {
    if (!JBCefApp.isSupported()) {
      throw new IllegalStateException("JCEF is not supported on this system");
    }

    var darkTheme = UIUtil.isUnderDarcula();

    var parameters = new HashMap<String, String>();
    parameters.put("var", "embed");
    var theme = darkTheme ? "dark" : "light";
    if (ExperimentalUI.isNewUI()) {
      theme += "-new-ui";
    }
    parameters.put("theme", theme);
    parameters.put("lang", Locale.getDefault().toLanguageTag().toLowerCase(Locale.ENGLISH));
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

    request.withQueryHandler(queryHandler);

    var title = IdeBundle.message("update.whats.new", ApplicationNamesInfo.getInstance().getFullProductName());
    HTMLEditorProvider.openEditor(project, title, request);
  }
}