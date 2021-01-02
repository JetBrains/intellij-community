// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class UnknownMissingSdkFixLocal extends UnknownSdkFixActionLocalBase implements UnknownSdkFixAction {
  private static final Logger LOG = Logger.getInstance(UnknownMissingSdkFixLocal.class);

  private @NotNull final UnknownSdkLocalSdkFix myFix;
  private @NotNull final UnknownSdk mySdk;

  UnknownMissingSdkFixLocal(@NotNull UnknownSdk sdk,
                            @NotNull UnknownSdkLocalSdkFix fix) {
    myFix = fix;
    mySdk = sdk;
  }

  @NotNull
  String getSdkNameForUi() {
    return UnknownMissingSdk.getSdkNameForUi(mySdk);
  }

  @NotNull
  UnknownSdkLocalSdkFix getLocalSdkFix() {
    return myFix;
  }

  @NotNull
  UnknownSdk getUnknownSdk() {
    return mySdk;
  }

  @Override
  public @NotNull @Nls String getActionTooltipText() {
    return SdkListPresenter.presentDetectedSdkPath(myFix.getExistingSdkHome(), 90, 40);
  }

  @Override
  public @Nullable Sdk getRegisteredSdkPrototype() {
    return myFix.getRegisteredSdkPrototype();
  }

  @Override
  public @NotNull @Nls String getActionShortText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("config.unknown.sdk.local", sdkTypeName, myFix.getPresentableVersionString());
  }

  public @NotNull @Nls String getActionAppliedMessage() {
    return ProjectBundle.message("notification.text.sdk.usage.is.set.to", getSdkNameForUi(), myFix.getVersionString());
  }

  @Override
  public @NotNull @Nls String getActionDetailedText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("label.text.use.for.unknown.sdk",
                                 sdkTypeName,
                                 myFix.getPresentableVersionString(),
                                 sdkTypeName,
                                 getSdkNameForUi());

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
      String actualSdkName = mySdk.getSdkName();
      if (actualSdkName == null) {
        actualSdkName = myFix.getSuggestedSdkName();
      }

      Sdk sdk = UnknownMissingSdkFix.createNewSdk(mySdk, myFix::getSuggestedSdkName);
      SdkModificator mod = sdk.getSdkModificator();
      mod.setHomePath(FileUtil.toSystemIndependentName(myFix.getExistingSdkHome()));
      mod.setVersionString(myFix.getVersionString());
      mod.commitChanges();

      mySdk.getSdkType().setupSdkPaths(sdk);
      myFix.configureSdk(sdk);
      UnknownMissingSdkFix.registerNewSdkInJdkTable(actualSdkName, sdk);

      LOG.info("Automatically set Sdk " + mySdk + " to " + myFix.getExistingSdkHome());
      return sdk;
    } catch (Throwable t) {
      LOG.warn("Failed to configure " + mySdk.getSdkType().getPresentableName() + " " + " for " + mySdk + " for path " + myFix + ". " + t.getMessage(), t);
      throw t;
    }
  }

  @Override
  public String toString() {
    return myFix.toString();
  }
}
