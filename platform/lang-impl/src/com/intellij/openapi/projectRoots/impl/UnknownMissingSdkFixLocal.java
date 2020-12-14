// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

class UnknownMissingSdkFixLocal implements UnknownSdkFixAction {
  private static final Logger LOG = Logger.getInstance(UnknownMissingSdkFixLocal.class);

  private @NotNull final String mySdkName;
  private @NotNull final UnknownSdkLocalSdkFix myFix;
  private @NotNull final UnknownSdk mySdk;

  UnknownMissingSdkFixLocal(@NotNull String sdkName,
                            @NotNull UnknownSdk sdk,
                            @NotNull UnknownSdkLocalSdkFix fix) {
    mySdkName = sdkName;
    myFix = fix;
    mySdk = sdk;
  }

  @NotNull
  String getSdkName() {
    return mySdkName;
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
    return getUsedSdkPath();
  }

  public @NotNull @Nls String getUsedSdkPath() {
    return SdkListPresenter.presentDetectedSdkPath(myFix.getExistingSdkHome(), 90, 40);
  }

  @Override
  public @NotNull @Nls String getActionShortText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("config.unknown.sdk.local", sdkTypeName, myFix.getPresentableVersionString());
  }

  public @NotNull @Nls String getActionAppliedMessage() {
    return ProjectBundle.message("notification.text.sdk.usage.is.set.to", mySdkName, myFix.getVersionString());
  }

  @Override
  public @NotNull @Nls String getActionDetailedText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("label.text.use.for.unknown.sdk",
                                 sdkTypeName,
                                 myFix.getPresentableVersionString(),
                                 sdkTypeName,
                                 mySdkName);

  }

  @Override
  public void applySuggestionAsync() {
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        UnknownSdkTracker.applyLocalFix(mySdk, myFix);
      } catch (Throwable t) {
        LOG.warn("Failed to configure " + mySdk.getSdkType().getPresentableName() + " " + " for " + mySdk + " for path " + myFix + ". " + t.getMessage(), t);
      }
    });
  }

  @Override
  public void applySuggestionModal(@NotNull ProgressIndicator indicator) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      UnknownSdkTracker.applyLocalFix(mySdk, myFix);
    });
  }

  @Override
  public String toString() {
    return myFix.toString();
  }
}
