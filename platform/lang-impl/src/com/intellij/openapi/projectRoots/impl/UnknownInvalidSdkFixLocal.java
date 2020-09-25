// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class UnknownInvalidSdkFixLocal implements UnknownSdkFixAction {
  private @NotNull final UnknownInvalidSdk mySdk;
  private @NotNull final Project myProject;
  private @NotNull final UnknownSdkLocalSdkFix myLocalSdkFix;

  UnknownInvalidSdkFixLocal(@NotNull UnknownInvalidSdk sdk,
                            @NotNull Project project,
                            @NotNull UnknownSdkLocalSdkFix localSdkFix) {
    mySdk = sdk;
    myProject = project;
    myLocalSdkFix = localSdkFix;
  }

  @Override
  public @NotNull @Nls String getActionKindText() {
    return ProjectBundle.message("config.unknown.sdk.local.verb");
  }

  @Override
  public @NotNull @Nls String getActionText() {
    String sdkTypeName = mySdk.mySdkType.getPresentableName();
    return ProjectBundle.message("config.unknown.sdk.local", sdkTypeName, myLocalSdkFix.getPresentableVersionString());
  }

  @Override
  public @Nullable @Nls String getCheckboxActionTooltip() {
    return SdkListPresenter.presentDetectedSdkPath(myLocalSdkFix.getExistingSdkHome(), 90, 40);
  }

  @Override
  public @NotNull @Nls String getCheckboxActionText() {
    String sdkTypeName = mySdk.mySdkType.getPresentableName();
    return ProjectBundle.message("checkbox.text.use.for.invalid.sdk",
                                 sdkTypeName,
                                 myLocalSdkFix.getPresentableVersionString(),
                                 sdkTypeName,
                                 mySdk.mySdk.getName());
  }

  @Override
  public void applySuggestionAsync() {
    applyLocalFix(myProject);
  }

  @Override
  public void applySuggestionModal(@NotNull ProgressIndicator indicator) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      applyLocalFix(myProject);
    });
  }

  private void applyLocalFix(@NotNull Project project) {
    String sdkFixVersionString = myLocalSdkFix.getVersionString();
    String sdkHome = myLocalSdkFix.getExistingSdkHome();

    mySdk.copySdk(project, sdkFixVersionString, sdkHome);
    myLocalSdkFix.configureSdk(mySdk.mySdk);
  }

}
