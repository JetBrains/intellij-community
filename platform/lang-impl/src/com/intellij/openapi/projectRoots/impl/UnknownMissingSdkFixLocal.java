// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class UnknownMissingSdkFixLocal extends UnknownSdkFixActionLocalBase implements UnknownSdkFixAction {
  private static final Logger LOG = Logger.getInstance(UnknownMissingSdkFixLocal.class);

  private final @NotNull UnknownSdkLocalSdkFix myFix;
  private final @NotNull UnknownSdk mySdk;

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

  @Override
  protected @NotNull Sdk applyLocalFix() {
    ThreadingAssertions.assertEventDispatchThread();

    try {
      String actualSdkName = mySdk.getSdkName();
      if (actualSdkName == null) {
        actualSdkName = myFix.getSuggestedSdkName();
      }

      Sdk sdk = UnknownMissingSdkFix.createNewSdk(mySdk, myFix::getSuggestedSdkName);
      SdkModificator mod = sdk.getSdkModificator();
      mod.setHomePath(FileUtil.toSystemIndependentName(myFix.getExistingSdkHome()));
      mod.setVersionString(myFix.getVersionString());
      ApplicationManager.getApplication().runWriteAction(() -> {
        mod.commitChanges();
      });

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
