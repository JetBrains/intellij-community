// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.whatsNew.DefaultWhatsNewQueryHandler;
import com.intellij.ide.whatsNew.WhatsNewPageProvider;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;

@ApiStatus.Internal
public final class WhatsNewAction extends AnAction implements DumbAware {
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
    if (project == null || !openEmbeddedWhatsNewPage(project, url, /*includePlatformData:*/ false)) {
      BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url));
    }
  }

  @ApiStatus.Internal
  public static void openWhatsNewPage(@NotNull Project project, @NotNull String url) {
    if (openEmbeddedWhatsNewPage(project, url, /*includePlatformData:*/ true)) {
      return;
    }

    var name = ApplicationNamesInfo.getInstance().getFullProductName();
    var version = ApplicationInfo.getInstance().getShortVersion();
    var notificationText = IdeBundle.message("whats.new.notification.text", name, version);
    UpdateCheckerFacade.getInstance().getNotificationGroupForIdeUpdateResults()
      .createNotification(notificationText, NotificationType.INFORMATION)
      .setIcon(AllIcons.Nodes.PpWeb)
      .setDisplayId("ide.whats.new")
      .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("whats.new.notification.action"), () -> BrowserUtil.browse(url)))
      .notify(project);
  }

  private static boolean openEmbeddedWhatsNewPage(Project project, String url, boolean includePlatformData) {
    if (ScreenReader.isActive()) return false;
    var provider = WhatsNewPageProvider.EP_NAME.findFirstSafe(WhatsNewPageProvider::isAvailable);
    if (provider == null) return false;
    provider.openWhatsNewPage(project, url, includePlatformData, new DefaultWhatsNewQueryHandler(project));
    return true;
  }
}
