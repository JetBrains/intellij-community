// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class UnknownInvalidSdkFixLocal extends UnknownSdkFixActionLocalBase implements UnknownSdkFixAction {
  private @NotNull final UnknownInvalidSdk mySdk;
  private @NotNull final UnknownSdkLocalSdkFix myFix;

  UnknownInvalidSdkFixLocal(@NotNull UnknownInvalidSdk sdk,
                            @NotNull UnknownSdkLocalSdkFix localSdkFix) {
    mySdk = sdk;
    myFix = localSdkFix;
  }

  @Override
  public @NotNull @Nls String getActionShortText() {
    String sdkTypeName = mySdk.mySdkType.getPresentableName();
    return ProjectBundle.message("config.unknown.sdk.local", sdkTypeName, myFix.getPresentableVersionString());
  }

  @Override
  public @Nullable @Nls String getActionTooltipText() {
    return SdkListPresenter.presentDetectedSdkPath(myFix.getExistingSdkHome(), 90, 40);
  }

  @Override
  public @Nullable Sdk getRegisteredSdkPrototype() {
    return myFix.getRegisteredSdkPrototype();
  }

  @Override
  public @NotNull @Nls String getActionDetailedText() {
    String sdkTypeName = mySdk.mySdkType.getPresentableName();
    return ProjectBundle.message("label.text.use.for.invalid.sdk",
                                 sdkTypeName,
                                 myFix.getPresentableVersionString(),
                                 sdkTypeName,
                                 mySdk.mySdk.getName());
  }

  @Override
  protected @NotNull String getSuggestedSdkHome() {
    return myFix.getExistingSdkHome();
  }

  @NotNull
  @Override
  protected Sdk applyLocalFix() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    try {
      String sdkFixVersionString = myFix.getVersionString();
      String sdkHome = myFix.getExistingSdkHome();

      mySdk.copySdk(sdkFixVersionString, sdkHome);
      myFix.configureSdk(mySdk.mySdk);
      return mySdk.mySdk;
    } catch (Throwable t) {
      Logger.getInstance(getClass()).warn("Failed to configure " + mySdk.getSdkType().getPresentableName() + " " + " for " + mySdk + " for path " + myFix + ". " + t.getMessage(), t);
      throw t;
    }
  }

  @Override
  public String toString() {
    return myFix.toString();
  }
}
