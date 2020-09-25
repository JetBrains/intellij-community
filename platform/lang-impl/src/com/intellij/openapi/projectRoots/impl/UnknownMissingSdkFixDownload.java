// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

final class UnknownMissingSdkFixDownload implements UnknownSdkFixAction {
  private @NotNull final Project myProject;
  private @NotNull final UnknownSdkDownloadableSdkFix myFix;
  private @NotNull final UnknownSdk mySdk;

  UnknownMissingSdkFixDownload(@NotNull Project project,
                               @NotNull UnknownSdk sdk,
                               @NotNull UnknownSdkDownloadableSdkFix fix) {
    myProject = project;
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
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("checkbox.text.download.for.missing.sdk",
                                 myFix.getDownloadDescription(),
                                 sdkTypeName,
                                 mySdk.getSdkName()
    );
  }

  @NotNull
  private UnknownSdkDownloadTask createDownloadTask() {
    return UnknownSdkTracker.createDownloadFixTask(mySdk, myFix, sdk -> {
    }, sdk -> {
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
