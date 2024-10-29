// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.intellij.notification.NotificationAction.createSimple;

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
public final class UnknownSdkBalloonNotification {
  public static @NotNull UnknownSdkBalloonNotification getInstance(@NotNull Project project) {
    return project.getService(UnknownSdkBalloonNotification.class);
  }

  private final Project myProject;

  public UnknownSdkBalloonNotification(@NotNull Project project) {
    myProject = project;
  }

  public void notifyFixedSdks(@NotNull List<UnknownMissingSdkFixLocal> localFixes) {
    if (localFixes.isEmpty()) return;

    Set<@Nls String> usages = new TreeSet<>();
    for (var entry : localFixes) {
      UnknownSdkLocalSdkFix fix = entry.getLocalSdkFix();
      String usageText = ProjectBundle.message("notification.text.sdk.usage.is.set.to", entry.getSdkNameForUi(), fix.getVersionString());
      usages.add(new HtmlBuilder()
                   .append(usageText)
                   .append(HtmlChunk.br())
                   .append(SdkListPresenter.presentDetectedSdkPath(fix.getExistingSdkHome()))
                   .toString());
    }

    @Nls String message = StringUtil.join(usages, "<br/><br/>");
    String title, change;
    if (localFixes.size() == 1) {
      var entry = localFixes.iterator().next();
      UnknownSdk info = entry.getUnknownSdk();
      String sdkTypeName = info.getSdkType().getPresentableName();
      title = ProjectBundle.message("notification.title.sdk.configured", sdkTypeName);
      change = ProjectBundle.message("notification.link.change.sdk", sdkTypeName);
    }
    else {
      title = ProjectBundle.message("notification.title.sdks.configured");
      change = ProjectBundle.message("notification.link.change.sdks");
    }

    if (message.isBlank()) return;

    NotificationGroupManager.getInstance().getNotificationGroup("Missing SDKs")
      .createNotification(title, message, NotificationType.INFORMATION)
      .setImportant(true)
      .addAction(createSimple(
        change,
        () -> ProjectSettingsService.getInstance(myProject).openProjectSettings()))
      .notify(myProject);
  }
}
