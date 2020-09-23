// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.notification.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.intellij.notification.NotificationAction.createSimple;

public class UnknownSdkBalloonNotification {
  public static @NotNull UnknownSdkBalloonNotification getInstance(@NotNull Project project) {
    return project.getService(UnknownSdkBalloonNotification.class);
  }

  private final Project myProject;

  public UnknownSdkBalloonNotification(@NotNull Project project) {
    myProject = project;
  }

  public void notifyFixedSdks(@NotNull Map<? extends UnknownSdk, UnknownSdkLocalSdkFix> localFixes) {
    if (localFixes.isEmpty()) return;

    Set<@Nls String> usages = new TreeSet<>();
    for (Map.Entry<? extends UnknownSdk, UnknownSdkLocalSdkFix> entry : localFixes.entrySet()) {
      UnknownSdkLocalSdkFix fix = entry.getValue();
      String usageText = ProjectBundle.message("notification.text.sdk.usage.is.set.to", entry.getKey().getSdkName(), fix.getVersionString());
      String usage = usageText + "<br/>" + SdkListPresenter.presentDetectedSdkPath(fix.getExistingSdkHome());
      usages.add(usage);
    }
    @Nls StringBuilder message = new StringBuilder();
    for (String usage : usages) {
      if (message.length() == 0) message.append("<br/><br/>");
      message.append(usage);
    }

    String title, change;
    if (localFixes.size() == 1) {
      Map.Entry<? extends UnknownSdk, UnknownSdkLocalSdkFix> entry = localFixes.entrySet().iterator().next();
      UnknownSdk info = entry.getKey();
      String sdkTypeName = info.getSdkType().getPresentableName();
      title = ProjectBundle.message("notification.title.sdk.configured", sdkTypeName);
      change = ProjectBundle.message("notification.link.change.sdk", sdkTypeName);
    }
    else {
      title = ProjectBundle.message("notification.title.sdks.configured");
      change = ProjectBundle.message("notification.link.change.sdks");
    }

    NotificationGroupManager.getInstance().getNotificationGroup("Missing SDKs")
      .createNotification(title, message.toString(), NotificationType.INFORMATION, null)
      .setImportant(true)
      .addAction(createSimple(
        change,
        () -> ProjectSettingsService.getInstance(myProject).openProjectSettings()))
      .notify(myProject);
  }
}
