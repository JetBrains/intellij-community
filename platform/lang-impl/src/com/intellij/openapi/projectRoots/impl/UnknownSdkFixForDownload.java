// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

class UnknownSdkFixForDownload extends UnknownSdkFix {
  private final @NotNull String mySdkName;
  private final @Nullable UnknownSdk mySdk;
  private final @Nullable UnknownSdkDownloadableSdkFix myFix;

  UnknownSdkFixForDownload(@NotNull Project project,
                           @NotNull String sdkName,
                           @NotNull SdkType sdkType,
                           @Nullable UnknownSdk sdk,
                           @Nullable UnknownSdkDownloadableSdkFix fix) {
    super(project, sdkType);
    mySdkName = sdkName;
    mySdk = sdk;
    myFix = fix;
  }

  @Override
  protected final @NotNull EditorNotificationPanel createNotificationPanelImpl(@NotNull Project project) {
    String sdkTypeName = mySdkType.getPresentableName();
    String notificationText = ProjectBundle.message("config.unknown.sdk.notification.text", sdkTypeName, mySdkName);
    String configureText = ProjectBundle.message("config.unknown.sdk.configure");

    boolean hasDownload = myFix != null && mySdk != null;
    String downloadText = hasDownload ? ProjectBundle.message("config.unknown.sdk.download", myFix.getDownloadDescription()) : "";
    String intentionActionText =
      hasDownload ? downloadText : ProjectBundle.message("config.unknown.sdk.configure.missing", sdkTypeName, mySdkName);

    EditorNotificationPanel notification = newNotificationPanel(intentionActionText);
    notification.setText(notificationText);

    if (hasDownload) {
      AtomicBoolean isRunning = new AtomicBoolean(false);
      notification.createActionLabel(downloadText, () -> {
        if (isRunning.compareAndSet(false, true)) {
          UnknownSdkTracker.getInstance(myProject).applyDownloadableFix(mySdk, myFix);
        }
      }, true);
    }

    notification.createActionLabel(configureText,
                                   UnknownSdkTracker
                                     .getInstance(myProject)
                                     .createSdkSelectionPopup(mySdkName, mySdkType),
                                   true
    );

    return notification;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("SdkFixInfo { name: ").append(mySdkName);
    if (myFix != null) {
      sb.append(", fix: ").append(myFix.getDownloadDescription());
    }
    sb.append("}");
    return sb.toString();
  }
}
