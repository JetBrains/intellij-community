// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.util.EmptyConsumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

final class UnknownMissingSdkFixDownload extends UnknownSdkFixActionDownloadBase implements UnknownSdkFixAction {
  private @NotNull final UnknownSdkDownloadableSdkFix myFix;
  private @NotNull final UnknownSdk mySdk;

  UnknownMissingSdkFixDownload(@NotNull UnknownSdk sdk,
                               @NotNull UnknownSdkDownloadableSdkFix fix) {
    myFix = fix;
    mySdk = sdk;
  }

  @NotNull
  String getSdkNameForUi() {
    return UnknownMissingSdk.getSdkNameForUi(mySdk);
  }

  @Override
  public @NotNull @Nls String getActionShortText() {
    return ProjectBundle.message("action.text.config.unknown.sdk.download", myFix.getDownloadDescription());
  }

  @Override
  public @NotNull @Nls String getActionDetailedText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("label.text.download.for.missing.sdk",
                                 myFix.getDownloadDescription(),
                                 sdkTypeName,
                                 getSdkNameForUi()
    );
  }

  @Override
  protected @NotNull UnknownSdkDownloadTask createTask() {
    return new UnknownSdkDownloadTask(mySdk,
                                      myFix,
                                      task -> UnknownMissingSdkFix.createNewSdk(mySdk, task::getSuggestedSdkName),
                                      EmptyConsumer.getInstance(),
                                      sdk -> {
                                        if (sdk == null) return;
                                        myFix.configureSdk(sdk);
                                        UnknownMissingSdkFix.registerNewSdkInJdkTable(sdk.getName(), sdk);
                                      });
  }

  @Override
  public String toString() {
    return myFix.toString();
  }
}
