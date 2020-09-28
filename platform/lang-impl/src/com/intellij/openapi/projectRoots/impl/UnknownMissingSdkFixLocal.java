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
  private @NotNull final UnknownSdkLocalSdkFix myLocalSdkFix;
  private @NotNull final UnknownSdk mySdk;

  UnknownMissingSdkFixLocal(@NotNull String sdkName,
                            @NotNull UnknownSdk sdk,
                            @NotNull UnknownSdkLocalSdkFix fix) {
    mySdkName = sdkName;
    myLocalSdkFix = fix;
    mySdk = sdk;
  }

  @NotNull
  String getSdkName() {
    return mySdkName;
  }

  @NotNull
  UnknownSdkLocalSdkFix getLocalSdkFix() {
    return myLocalSdkFix;
  }

  @NotNull
  UnknownSdk getUnknownSdk() {
    return mySdk;
  }

  @Override
  public @NotNull @Nls String getActionKindText() {
    return "TODO";
  }

  @Override
  public @NotNull @Nls String getCheckboxActionTooltip() {
    return getUsedSdkPath();
  }

  public @NotNull @Nls String getUsedSdkPath() {
    return SdkListPresenter.presentDetectedSdkPath(myLocalSdkFix.getExistingSdkHome(), 90, 40);
  }

  @Override
  public @NotNull @Nls String getActionText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("config.unknown.sdk.local", sdkTypeName, myLocalSdkFix.getPresentableVersionString());
  }

  public @NotNull @Nls String getActionAppliedMessage() {
    return ProjectBundle.message("notification.text.sdk.usage.is.set.to", mySdkName, myLocalSdkFix.getVersionString());
  }

  @Override
  public @NotNull @Nls String getCheckboxActionText() {
    String sdkTypeName = mySdk.getSdkType().getPresentableName();
    return ProjectBundle.message("checkbox.text.use.for.unknown.sdk",
                                 sdkTypeName,
                                 myLocalSdkFix.getPresentableVersionString(),
                                 sdkTypeName,
                                 mySdkName);

  }

  @Override
  public void applySuggestionAsync() {
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        UnknownSdkTracker.applyLocalFix(mySdk, myLocalSdkFix);
      } catch (Throwable t) {
        LOG.warn("Failed to configure " + mySdk.getSdkType().getPresentableName() + " " + " for " + mySdk + " for path " + myLocalSdkFix + ". " + t.getMessage(), t);
      }
    });
  }

  @Override
  public void applySuggestionModal(@NotNull ProgressIndicator indicator) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      UnknownSdkTracker.applyLocalFix(mySdk, myLocalSdkFix);
    });
  }
}
