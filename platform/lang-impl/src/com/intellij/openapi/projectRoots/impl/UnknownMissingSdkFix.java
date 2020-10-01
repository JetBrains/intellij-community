// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class UnknownMissingSdkFix implements UnknownSdkFix {
  @Nullable private final Project myProject;
  @NotNull private final UnknownSdk mySdk;
  @Nullable private final UnknownSdkFixAction myAction;

  UnknownMissingSdkFix(@Nullable Project project,
                       @NotNull UnknownSdk unknownSdk,
                       @Nullable UnknownSdkFixAction action) {
    myProject = project;
    mySdk = unknownSdk;
    myAction = action;
  }

  @NotNull
  String getSdkNameForUi() {
    return UnknownMissingSdk.getSdkNameForUi(mySdk);
  }

  @Override
  public boolean isRelevantFor(@NotNull Project project) {
    return myProject == null || project == myProject;
  }

  @Override
  public boolean isRelevantFor(@NotNull Project project, @NotNull VirtualFile file) {
    return isRelevantFor(project) && mySdk.getSdkType().isRelevantForFile(project, file);
  }

  private @NotNull SdkType getSdkType() {
    return mySdk.getSdkType();
  }

  @Override
  public @Nullable UnknownSdkFixAction getSuggestedFixAction() {
    return myAction;
  }

  @Override
  public @Nls @NotNull String getIntentionActionText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("config.unknown.sdk.configure.missing", sdkTypeName, getSdkNameForUi());
  }

  @Override
  public @NotNull EditorNotificationPanel.ActionHandler getConfigureActionHandler(@NotNull Project project) {
    return UnknownSdkTracker
      .getInstance(project)
      .createSdkSelectionPopup(getSdkNameForUi(), mySdk.getSdkType());
  }

  @Override
  public @Nls @NotNull String getNotificationText() {
    String sdkTypeName = getSdkType().getPresentableName();
    return ProjectBundle.message("notification.text.config.unknown.sdk", sdkTypeName, getSdkNameForUi());
  }

  @Override
  public @Nls @NotNull String getSdkTypeAndNameText() {
    String sdkTypeName = getSdkType().getPresentableName();
    return ProjectBundle.message("dialog.text.resolving.sdks.item", sdkTypeName, getSdkNameForUi());
  }

  @Override
  public @Nls @NotNull String getConfigureActionText() {
    return ProjectBundle.message("action.text.config.unknown.sdk.configure");
  }

  @Override
  public String toString() {
    return "SdkFixInfo {" + mySdk + ", " + myAction + "}";
  }
}
