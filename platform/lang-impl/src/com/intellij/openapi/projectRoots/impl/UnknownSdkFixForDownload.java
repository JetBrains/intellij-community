// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

class UnknownSdkFixForDownload extends UnknownSdkFix {
  private final @NotNull String mySdkName;
  private final @Nullable DownloadFixActionImpl myDownloadFixAction;

  UnknownSdkFixForDownload(@NotNull Project project,
                           @NotNull String sdkName,
                           @NotNull SdkType sdkType,
                           @Nullable UnknownSdk sdk,
                           @Nullable UnknownSdkDownloadableSdkFix fix) {
    super(project, sdkType);
    mySdkName = sdkName;
    myDownloadFixAction = fix != null && sdk != null ? new DownloadFixActionImpl(fix, sdk) : null;
  }

  @Override
  public @Nullable SuggestedFixAction getSuggestedFixAction() {
    return myDownloadFixAction;
  }

  private class DownloadFixActionImpl implements SuggestedFixAction {
    @NotNull final UnknownSdkDownloadableSdkFix myFix;
    @NotNull final UnknownSdk mySdk;

    private DownloadFixActionImpl(@NotNull UnknownSdkDownloadableSdkFix fix,
                                  @NotNull UnknownSdk sdk) {
      myFix = fix;
      mySdk = sdk;
    }

    @Override
    public @NotNull @Nls String getActionKindText() {
      return ProjectBundle.message("config.unknown.sdk.download.verb");
    }

    @Override
    public @NotNull @Nls String getActionText() {
      return ProjectBundle.message("config.unknown.sdk.download", myFix.getDownloadDescription());
    }

    @Override
    public @NotNull @Nls String getCheckboxActionText() {
      String sdkTypeName = mySdkType.getPresentableName();
      return ProjectBundle.message("checkbox.text.download.for.missing.sdk",
                                   myFix.getDownloadDescription(),
                                   sdkTypeName,
                                   mySdkName
                                   );
    }
  }

  @Override
  public @Nls @NotNull String getNotificationText() {
    String sdkTypeName = mySdkType.getPresentableName();
    return ProjectBundle.message("config.unknown.sdk.notification.text", sdkTypeName, mySdkName);
  }

  @Override
  public @Nls @NotNull String getSdkTypeAndNameText() {
    String sdkTypeName = mySdkType.getPresentableName();
    return ProjectBundle.message("dialog.text.resolving.sdks.item", sdkTypeName, mySdkName);
  }

  @Override
  protected final @NotNull EditorNotificationPanel createNotificationPanelImpl(@NotNull Project project) {
    EditorNotificationPanel notification;

    if (myDownloadFixAction != null) {
      String actionText = myDownloadFixAction.getActionText();
      notification = newNotificationPanel(actionText);
      AtomicBoolean isRunning = new AtomicBoolean(false);
      notification.createActionLabel(actionText, () -> {
        if (isRunning.compareAndSet(false, true)) {
          UnknownSdkTracker.getInstance(myProject).applyDownloadableFix(myDownloadFixAction.mySdk, myDownloadFixAction.myFix);
        }
      }, true);
    } else {
      String sdkTypeName = mySdkType.getPresentableName();
      String intentionActionText = ProjectBundle.message("config.unknown.sdk.configure.missing", sdkTypeName, mySdkName);
      notification = newNotificationPanel(intentionActionText);
    }

    notification.setText(getNotificationText());
    notification.createActionLabel(ProjectBundle.message("config.unknown.sdk.configure"),
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
    if (myDownloadFixAction != null) {
      sb.append(", fix: ").append(myDownloadFixAction.myFix.getDownloadDescription());
    }
    sb.append("}");
    return sb.toString();
  }
}
