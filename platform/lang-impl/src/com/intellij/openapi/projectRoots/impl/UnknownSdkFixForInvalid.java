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
import org.jetbrains.annotations.Nullable;

final class UnknownSdkFixForInvalid extends UnknownSdkFix {
  private final @NotNull String mySdkName;
  private final @NotNull UnknownInvalidSdk mySdk;
  private final @Nullable UnknownSdkLocalSdkFix myLocalFix;
  private final @Nullable DownloadFixAction myDownloadFixAction;

  UnknownSdkFixForInvalid(@NotNull Project project, @NotNull UnknownInvalidSdk invalidSdk) {
    super(project, invalidSdk.mySdkType);
    mySdkName = invalidSdk.getSdkName();
    mySdk = invalidSdk;
    myLocalFix = mySdk.myLocalSdkFix;
    UnknownSdkDownloadableSdkFix downloadFix = mySdk.myDownloadableSdkFix;
    myDownloadFixAction = downloadFix != null ? new DownloadFixAction(downloadFix) : null;
  }

  @Override
  public @Nullable DownloadFixAction getDownloadAction() {
    return myDownloadFixAction;
  }

  @Override
  protected final @NotNull EditorNotificationPanel createNotificationPanelImpl(@NotNull Project project) {
    String sdkTypeName = mySdkType.getPresentableName();
    String notificationText = ProjectBundle.message("config.invalid.sdk.notification.text", sdkTypeName, mySdkName);
    String configureText = ProjectBundle.message("config.invalid.sdk.configure");
    String intentionActionText = ProjectBundle.message("config.invalid.sdk.configure.missing", sdkTypeName, mySdkName);

    String localText = "";
    String localTextTooltip = "";
    if (myLocalFix != null) {
      localText =
      intentionActionText = ProjectBundle.message("config.unknown.sdk.local", sdkTypeName, myLocalFix.getPresentableVersionString());
      localTextTooltip = SdkListPresenter.presentDetectedSdkPath(myLocalFix.getExistingSdkHome(), 90, 40);
    }

    EditorNotificationPanel notification = newNotificationPanel(intentionActionText);
    notification.setText(notificationText);

    if (myLocalFix != null) {
      HyperlinkLabel actionLabel = notification.createActionLabel(localText, () -> {
        mySdk.applyLocalFix(project);
      }, true);
      actionLabel.setToolTipText(localTextTooltip);
    }
    else if (myDownloadFixAction != null) {
      notification.createActionLabel(myDownloadFixAction.getActionText(), () -> mySdk.applyDownloadFix(myProject), true);
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
