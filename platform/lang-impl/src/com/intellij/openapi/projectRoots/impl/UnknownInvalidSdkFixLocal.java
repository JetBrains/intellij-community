// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class UnknownInvalidSdkFixLocal extends UnknownSdkFixActionLocalBase implements UnknownSdkFixAction {
  private final @NotNull UnknownInvalidSdk mySdk;
  private final @NotNull UnknownSdkLocalSdkFix myFix;

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

  @Override
  protected @NotNull Sdk applyLocalFix() {
    ThreadingAssertions.assertEventDispatchThread();
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
