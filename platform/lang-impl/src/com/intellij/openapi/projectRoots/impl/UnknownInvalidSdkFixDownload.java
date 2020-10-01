// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class UnknownInvalidSdkFixDownload implements UnknownSdkFixAction {
  private @NotNull final UnknownInvalidSdk mySdk;
  private @NotNull final UnknownSdkDownloadableSdkFix myFix;

  UnknownInvalidSdkFixDownload(@NotNull UnknownInvalidSdk sdk,
                               @NotNull UnknownSdkDownloadableSdkFix fix) {
    mySdk = sdk;
    myFix = fix;
  }

  @Override
  public @NotNull @Nls String getActionShortText() {
    return ProjectBundle.message("action.text.config.unknown.sdk.download", myFix.getDownloadDescription());
  }

  @Override
  public @NotNull @Nls String getActionDetailedText() {
    String sdkTypeName = mySdk.mySdkType.getPresentableName();
    return ProjectBundle.message("label.text.download.for.invalid.sdk",
                                 myFix.getDownloadDescription(),
                                 sdkTypeName,
                                 mySdk.mySdk.getName()
    );
  }

  @Override
  public void applySuggestionAsync(@Nullable Project project) {
    newSdkDownloadTask().runAsync(project);
  }

  @Override
  public void applySuggestionModal(@NotNull ProgressIndicator indicator) {
    newSdkDownloadTask().runBlocking(indicator);
  }

  @NotNull
  UnknownSdkDownloadTask newSdkDownloadTask() {
    return new UnknownSdkDownloadTask(
      mySdk,
      myFix,
      __ -> mySdk.mySdk,
      __ -> { },
      __ -> { }
    );
  }

  @Override
  public String toString() {
    return myFix.toString();
  }
}
