// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class UnknownSdkFixForDownload extends UnknownSdkFix {
  private final @NotNull String mySdkName;
  private final @Nullable DownloadMissingFixAction myDownloadFixAction;

  UnknownSdkFixForDownload(@NotNull Project project,
                           @NotNull String sdkName,
                           @NotNull SdkType sdkType,
                           @Nullable UnknownSdk sdk,
                           @Nullable UnknownSdkDownloadableSdkFix fix) {
    super(project, sdkType);
    mySdkName = sdkName;
    myDownloadFixAction = fix != null && sdk != null ? new DownloadMissingFixAction(fix, sdk) : null;
  }

  @Override
  public @Nullable UnknownSdkFixAction getSuggestedFixAction() {
    return myDownloadFixAction;
  }

  private class DownloadMissingFixAction implements UnknownSdkFixAction {
    @NotNull final UnknownSdkDownloadableSdkFix myFix;
    @NotNull final UnknownSdk mySdk;

    private DownloadMissingFixAction(@NotNull UnknownSdkDownloadableSdkFix fix,
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

    @NotNull
    private UnknownSdkDownloadTask createDownloadTask() {
      return UnknownSdkTracker.createDownloadFixTask(mySdk, myFix, sdk -> {}, sdk -> {
        if (sdk != null) {
          UnknownSdkTracker.getInstance(myProject).updateUnknownSdksNow();
        }
      });
    }

    @Override
    public void applySuggestionAsync() {
      createDownloadTask().runAsync(myProject);
    }

    @Override
    public void applySuggestionModal(@NotNull ProgressIndicator indicator) {
      createDownloadTask().runBlocking(indicator);
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
  protected final @NotNull EditorNotificationPanelWrapper createNotificationPanelImpl() {
    EditorNotificationPanelWrapper notification;

    if (myDownloadFixAction != null) {
      notification = createNotificationPanelWithMainAction(myDownloadFixAction);
    } else {
      String sdkTypeName = mySdkType.getPresentableName();
      String intentionActionText = ProjectBundle.message("config.unknown.sdk.configure.missing", sdkTypeName, mySdkName);
      notification = newNotificationPanel(intentionActionText);
    }

    notification.createActionLabel(ProjectBundle.message("config.unknown.sdk.configure"),
                                   UnknownSdkTracker
                                     .getInstance(myProject)
                                     .createSdkSelectionPopup(mySdkName, mySdkType));

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
