// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.Nls;
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
  public @Nls @NotNull String getNotificationText() {
    String sdkTypeName = mySdkType.getPresentableName();
    return ProjectBundle.message("config.invalid.sdk.notification.text", sdkTypeName, mySdkName);
  }

  @Override
  protected final @NotNull EditorNotificationPanel createNotificationPanelImpl(@NotNull Project project) {
    String sdkTypeName = mySdkType.getPresentableName();
    EditorNotificationPanel notification;
    if (myLocalFix != null) {
      String intentionActionText = ProjectBundle.message("config.unknown.sdk.local", sdkTypeName, myLocalFix.getPresentableVersionString());
      String localTextTooltip = SdkListPresenter.presentDetectedSdkPath(myLocalFix.getExistingSdkHome(), 90, 40);
      notification = newNotificationPanel(intentionActionText);
      HyperlinkLabel actionLabel = notification.createActionLabel(intentionActionText, () -> {
        mySdk.applyLocalFix(project);
      }, true);
      actionLabel.setToolTipText(localTextTooltip);
    }
    else if (myDownloadFixAction != null)  {
      String intentionActionText = myDownloadFixAction.getActionText();
      notification = newNotificationPanel(intentionActionText);
      notification.createActionLabel(intentionActionText, () -> mySdk.applyDownloadFix(myProject), true);
    } else {
      String intentionActionText = ProjectBundle.message("config.invalid.sdk.configure.missing", sdkTypeName, mySdkName);
      notification = newNotificationPanel(intentionActionText);
    }

    notification.setText(getNotificationText());
    notification.createActionLabel(ProjectBundle.message("config.invalid.sdk.configure"), mySdk.createSdkSelectionPopup(project), true);
    return notification;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("InvalidSdkFixInfo { name: ").append(mySdkName);
    if (mySdk.myLocalSdkFix != null) {
      sb.append(", fix: ").append(mySdk.myLocalSdkFix.getExistingSdkHome());
    }
    if (myDownloadFixAction != null) {
      sb.append(", fix: ").append(myDownloadFixAction.getFix().getDownloadDescription());
    }
    sb.append("}");
    return sb.toString();
  }
}
