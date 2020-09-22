// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;

final class UnknownSdkFixForInvalid extends UnknownSdkFix {
  private final @NotNull String mySdkName;
  private final @NotNull UnknownInvalidSdk mySdk;

  UnknownSdkFixForInvalid(@NotNull Project project, @NotNull UnknownInvalidSdk invalidSdk) {
    super(project, invalidSdk.mySdkType);
    mySdkName = invalidSdk.getSdkName();
    mySdk = invalidSdk;
  }

  @Override
  protected final @NotNull EditorNotificationPanel createNotificationPanelImpl(@NotNull Project project) {
    String sdkTypeName = mySdkType.getPresentableName();
    String notificationText = ProjectBundle.message("config.invalid.sdk.notification.text", sdkTypeName, mySdkName);
    String configureText = ProjectBundle.message("config.invalid.sdk.configure");

    UnknownSdkLocalSdkFix localFix = mySdk.myLocalSdkFix;
    UnknownSdkDownloadableSdkFix downloadFix = mySdk.myDownloadableSdkFix;

    String intentionActionText = ProjectBundle.message("config.invalid.sdk.configure.missing", sdkTypeName, mySdkName);

    String localText = "";
    String localTextTooltip = "";
    if (localFix != null) {
      localText =
      intentionActionText = ProjectBundle.message("config.unknown.sdk.local", sdkTypeName, localFix.getPresentableVersionString());
      localTextTooltip = SdkListPresenter.presentDetectedSdkPath(localFix.getExistingSdkHome(), 90, 40);
    }

    String downloadText = downloadFix != null
                          ? intentionActionText = ProjectBundle.message("config.unknown.sdk.download", downloadFix.getDownloadDescription())
                          : "";
    EditorNotificationPanel notification = newNotificationPanel(intentionActionText);
    notification.setText(notificationText);

    if (localFix != null) {
      HyperlinkLabel actionLabel = notification.createActionLabel(localText, () -> {
        mySdk.applyLocalFix(project);
      }, true);
      actionLabel.setToolTipText(localTextTooltip);
    }
    else if (downloadFix != null) {
      notification.createActionLabel(downloadText, () -> mySdk.applyDownloadFix(myProject), true);
    }

    notification.createActionLabel(configureText, mySdk.createSdkSelectionPopup(project), true);

    return notification;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("InvalidSdkFixInfo { name: ").append(mySdkName);
    if (mySdk.myLocalSdkFix != null) {
      sb.append(", fix: ").append(mySdk.myLocalSdkFix.getExistingSdkHome());
    }
    if (mySdk.myDownloadableSdkFix != null) {
      sb.append(", fix: ").append(mySdk.myDownloadableSdkFix.getDownloadDescription());
    }
    sb.append("}");
    return sb.toString();
  }
}
