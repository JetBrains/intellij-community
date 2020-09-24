// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class UnknownSdkFixForInvalid extends UnknownSdkFix {
  private final @NotNull String mySdkName;
  private final @NotNull UnknownInvalidSdk mySdk;
  private final @Nullable UnknownSdkFixForInvalid.DownloadInvalidFixAction myDownloadFixAction;
  private final @Nullable LocalFixAction myLocalFixAction;

  UnknownSdkFixForInvalid(@NotNull Project project, @NotNull UnknownInvalidSdk invalidSdk) {
    super(project, invalidSdk.mySdkType);
    mySdkName = invalidSdk.getSdkName();
    mySdk = invalidSdk;
    UnknownSdkLocalSdkFix myLocalFix = mySdk.myLocalSdkFix;
    UnknownSdkDownloadableSdkFix downloadFix = mySdk.myDownloadableSdkFix;
    myDownloadFixAction = downloadFix != null ? new DownloadInvalidFixAction(downloadFix) : null;
    myLocalFixAction = myLocalFix != null ? new LocalFixAction(myLocalFix) : null;
  }

  @Override
  public @Nullable SuggestedFixAction getSuggestedFixAction() {
    if (myLocalFixAction != null) return myLocalFixAction;
    return myDownloadFixAction;
  }

  @Override
  public @Nls @NotNull String getNotificationText() {
    String sdkTypeName = mySdkType.getPresentableName();
    return ProjectBundle.message("config.invalid.sdk.notification.text", sdkTypeName, mySdkName);
  }

  @Override
  public @Nls @NotNull String getSdkTypeAndNameText() {
    String sdkTypeName = mySdkType.getPresentableName();
    return ProjectBundle.message("dialog.text.resolving.sdks.item", sdkTypeName, mySdkName);
  }

  private class DownloadInvalidFixAction implements SuggestedFixAction {
    @NotNull final UnknownSdkDownloadableSdkFix myFix;

    private DownloadInvalidFixAction(@NotNull UnknownSdkDownloadableSdkFix fix) {
      myFix = fix;
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

    @Override
    public void applySuggestionAsync() {
      mySdk.applyDownloadFix(myProject);
    }

    @Override
    public void applySuggestionModal(@NotNull ProgressIndicator indicator) {
      throw new RuntimeException("TODO");
    }
  }

  private class LocalFixAction implements SuggestedFixAction {
    @NotNull final UnknownSdkLocalSdkFix myFix;

    private LocalFixAction(@NotNull UnknownSdkLocalSdkFix localSdkFix) {
      myFix = localSdkFix;
    }

    @Override
    public @NotNull @Nls String getActionKindText() {
      return ProjectBundle.message("config.unknown.sdk.local.verb");
    }

    @Override
    public @NotNull @Nls String getActionText() {
      String sdkTypeName = mySdkType.getPresentableName();
      return ProjectBundle.message("config.unknown.sdk.local", sdkTypeName, myFix.getPresentableVersionString());
    }

    @Override
    public @Nullable @Nls String getCheckboxActionTooltip() {
      return SdkListPresenter.presentDetectedSdkPath(myFix.getExistingSdkHome(), 90, 40);
    }

    @Override
    public @NotNull @Nls String getCheckboxActionText() {
      String sdkTypeName = mySdkType.getPresentableName();
      return ProjectBundle.message("checkbox.text.use.for.invalid.sdk",
                                   sdkTypeName,
                                   myFix.getPresentableVersionString(),
                                   sdkTypeName,
                                   mySdkName);
    }

    @Override
    public void applySuggestionAsync() {
      mySdk.applyLocalFix(myProject);
    }

    @Override
    public void applySuggestionModal(@NotNull ProgressIndicator indicator) {
      throw new RuntimeException("TODO");
    }
  }

  @Override
  protected final @NotNull EditorNotificationPanelWrapper createNotificationPanelImpl() {
    String sdkTypeName = mySdkType.getPresentableName();
    EditorNotificationPanelWrapper notification;

    var fixAction = getSuggestedFixAction();
    if (fixAction != null) {
      notification = createNotificationPanelWithMainAction(fixAction);
    } else {
      notification = newNotificationPanel(ProjectBundle.message("config.invalid.sdk.configure.missing", sdkTypeName, mySdkName));
    }

    notification.createActionLabel(ProjectBundle.message("config.invalid.sdk.configure"), mySdk.createSdkSelectionPopup(myProject));
    return notification;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("InvalidSdkFixInfo { name: ").append(mySdkName);
    if (myLocalFixAction != null) {
      sb.append(", fix: ").append(myLocalFixAction.myFix.getExistingSdkHome());
    }
    if (myDownloadFixAction != null) {
      sb.append(", fix: ").append(myDownloadFixAction.myFix.getDownloadDescription());
    }
    sb.append("}");
    return sb.toString();
  }
}
