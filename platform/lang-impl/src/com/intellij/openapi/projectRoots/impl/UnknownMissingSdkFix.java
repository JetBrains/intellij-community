// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnknownMissingSdkFix implements UnknownSdkFix {
  @NotNull private final Project myProject;
  @NotNull private final String mySdkName;
  @NotNull private final SdkType mySdkType;
  @Nullable private final UnknownSdkFixAction myAction;

  public UnknownMissingSdkFix(@NotNull Project project,
                              @NotNull String sdkName,
                              @NotNull SdkType sdkType,
                              @Nullable UnknownSdkFixAction action) {
    myProject = project;
    mySdkName = sdkName;
    mySdkType = sdkType;
    myAction = action;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull SdkType getSdkType() {
    return mySdkType;
  }

  @Override
  public @Nullable UnknownSdkFixAction getSuggestedFixAction() {
    return myAction;
  }

  @Override
  public @Nls @NotNull String getIntentionActionText() {
    String sdkTypeName = mySdkType.getPresentableName();
    return ProjectBundle.message("config.unknown.sdk.configure.missing", sdkTypeName, mySdkName);
  }

  @Override
  public @NotNull EditorNotificationPanel.ActionHandler getConfigureActionHandler() {
    return UnknownSdkTracker
      .getInstance(myProject)
      .createSdkSelectionPopup(mySdkName, mySdkType);
  }

  @Override
  public @Nls @NotNull String getNotificationText() {
    String sdkTypeName = getSdkType().getPresentableName();
    return ProjectBundle.message("notification.text.config.unknown.sdk", sdkTypeName, mySdkName);
  }

  @Override
  public @Nls @NotNull String getSdkTypeAndNameText() {
    String sdkTypeName = getSdkType().getPresentableName();
    return ProjectBundle.message("dialog.text.resolving.sdks.item", sdkTypeName, mySdkName);
  }

  @Override
  public @Nls @NotNull String getConfigureActionText() {
    return ProjectBundle.message("action.text.config.unknown.sdk.configure");
  }

  @Override
  public String toString() {
    return "SdkFixInfo { name: " + mySdkName + ", " + myAction + "}";
  }
}
