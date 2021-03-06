// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

class UnknownInvalidSdkFixDownload extends UnknownSdkFixActionDownloadBase implements UnknownSdkFixAction {
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
  protected @NotNull UnknownSdkDownloadTask createTask() {
    return new UnknownSdkDownloadTask(
      mySdk,
      myFix,
      __ -> mySdk.mySdk
    );
  }

  @Override
  public String toString() {
    return myFix.toString();
  }
}
