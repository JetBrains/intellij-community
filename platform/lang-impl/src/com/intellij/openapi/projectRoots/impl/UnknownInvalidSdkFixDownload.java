// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkMultipleDownloadsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class UnknownInvalidSdkFixDownload extends UnknownSdkFixActionDownloadBase implements UnknownSdkFixAction {
  private final @NotNull UnknownInvalidSdk mySdk;
  private final @NotNull UnknownSdkDownloadableSdkFix myFix;

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
  protected @NotNull String getDownloadDescription() {
    return myFix.getDownloadDescription();
  }

  @Override
  protected @Nullable String getSdkLookupReason() {
    return myFix.getSdkLookupReason();
  }

  @Override
  public boolean supportsSdkChoice() {
    return myFix instanceof UnknownSdkMultipleDownloadsFix<?>;
  }

  @Override
  public @NotNull @Nls String getChoiceActionText() {
    return ProjectBundle.message("sdk.choice.action.text", mySdk.getSdkType().getPresentableName());
  }

  @Override
  public boolean chooseSdk() {
    if (myFix instanceof UnknownSdkMultipleDownloadsFix<?> multipleDownloadsFix) {
      if (multipleDownloadsFix.chooseItem(mySdk.mySdkType.getPresentableName())) {
        giveConsent();
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return myFix.toString();
  }
}
