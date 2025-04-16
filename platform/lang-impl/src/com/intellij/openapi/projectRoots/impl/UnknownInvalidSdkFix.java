// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class UnknownInvalidSdkFix implements UnknownSdkFix {
  private static final Logger LOG = Logger.getInstance(UnknownInvalidSdkFix.class);

  private final @NotNull Project myProject;
  private final @NotNull String mySdkName;
  private final @NotNull UnknownInvalidSdk mySdk;
  private final @Nullable UnknownSdkFixAction myAction;

  UnknownInvalidSdkFix(@NotNull Project project,
                       @NotNull UnknownInvalidSdk invalidSdk,
                       @Nullable UnknownSdkFixAction action) {
    myProject = project;
    mySdkName = invalidSdk.getSdkName();
    mySdk = invalidSdk;
    myAction = action;
  }

  @Override
  public boolean isRelevantFor(@NotNull Project project) {
    return myProject == project;
  }

  @Override
  public boolean isRelevantFor(@NotNull Project project, @NotNull VirtualFile file) {
    return isRelevantFor(project) && mySdk.getSdkType().isRelevantForFile(project, file);
  }

  private @NotNull SdkType getSdkType() {
    return mySdk.mySdkType;
  }

  @Override
  public @Nls @NotNull String getConfigureActionText() {
    return ProjectBundle.message("action.text.config.invalid.sdk.configure");
  }

  @Override
  public @NotNull EditorNotificationPanel.ActionHandler getConfigureActionHandler(@NotNull Project project) {
      String sdkName = mySdk.mySdk.getName();
      return SdkPopupFactory
        .newBuilder()
        .withProject(project)
        // filter out the invalid sdk
        .withSdkFilter(sdk -> !Objects.equals(sdk.getName(), sdkName))
        .withSdkTypeFilter(type -> Objects.equals(type, mySdk.mySdkType))
        .onSdkSelected(sdk -> {
          String homePath = sdk.getHomePath();
          String versionString = sdk.getVersionString();
          if (homePath != null && versionString != null) {
            mySdk.copySdk(versionString, homePath);
          } else {
            LOG.warn("Newly added SDK has invalid home or version: " + sdk + ", home=" + homePath + " version=" + versionString);
          }
          project.getService(UnknownSdkCheckerService.class).checkUnknownSdks();
        })
        .buildEditorNotificationPanelHandler();
  }

  @Override
  public @Nls @NotNull String getIntentionActionText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("config.invalid.sdk.configure.missing", sdkTypeName, mySdkName);
  }

  @Override
  public @Nullable UnknownSdkFixAction getSuggestedFixAction() {
    return myAction;
  }

  @Override
  public @Nls @NotNull String getNotificationText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("notification.text.config.invalid.sdk", sdkTypeName, mySdkName);
  }

  @Override
  public @Nls @NotNull String getSdkTypeAndNameText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("dialog.text.resolving.sdks.item", sdkTypeName, mySdkName);
  }

  @Override
  public String toString() {
    return "InvalidSdkFixInfo { name: " + mySdkName + ", " + myAction + "}";
  }
}
