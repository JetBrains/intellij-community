// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UnknownInvalidSdk implements UnknownSdk {
  private static final Logger LOG = Logger.getInstance(UnknownInvalidSdk.class);

  @NotNull final Sdk mySdk;
  @NotNull final SdkType mySdkType;

  UnknownInvalidSdk(@NotNull Sdk sdk, @NotNull SdkType sdkType) {
    mySdk = sdk;
    mySdkType = sdkType;
  }

  @NotNull
  @Override
  public SdkType getSdkType() {
    return mySdkType;
  }

  @Override
  @NotNull
  public String getSdkName() {
    return mySdk.getName();
  }

  @Override
  @Nullable
  public String getExpectedVersionString() {
    return mySdk.getVersionString();
  }

  void copySdk(@NotNull String sdkFixVersionString,
               @NotNull String sdkHome) {
    WriteAction.run(() -> {
      SdkModificator mod = mySdk.getSdkModificator();
      mod.setVersionString(sdkFixVersionString);
      mod.setHomePath(sdkHome);
      mod.commitChanges();

      mySdkType.setupSdkPaths(mySdk);
    });
  }

  @NotNull
  static List<UnknownInvalidSdk> resolveInvalidSdks(@NotNull List<? extends Sdk> usedSdks) {
    List<UnknownInvalidSdk> result = new ArrayList<>();
    for (Sdk sdk : usedSdks) {
      if (SdkDownloadTracker.getInstance().isDownloading(sdk)) continue;

      UnknownInvalidSdk invalidSdk = resolveInvalidSdk(sdk);
      if (invalidSdk != null) {
        result.add(invalidSdk);
      }
    }
    return result;
  }

  @Nullable
  private static UnknownInvalidSdk resolveInvalidSdk(@NotNull Sdk sdk) {
    SdkTypeId type = sdk.getSdkType();
    if (!(type instanceof SdkType)) return null;
    SdkType sdkType = (SdkType)type;

    //for tests
    //noinspection TestOnlyProblems
    if (ApplicationManager.getApplication().isUnitTestMode() && sdk instanceof MockSdk) {
      return null;
    }

    try {
      String homePath = sdk.getHomePath();
      if (homePath != null && sdkType.isValidSdkHome(homePath)) {
        return null;
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.warn("Failed to validate SDK " + sdk + ". " + e.getMessage(), e);
      return null;
    }

    return new UnknownInvalidSdk(sdk, sdkType);
  }

  @NotNull
  public UnknownInvalidSdkFix buildFix(@NotNull Project project,
                                       @Nullable UnknownSdkLocalSdkFix localSdkFix,
                                       @Nullable UnknownSdkDownloadableSdkFix downloadableSdkFix) {
    UnknownSdkFixAction action = null;
    if (localSdkFix != null) {
      action = new UnknownInvalidSdkFixLocal(this, localSdkFix);
    } else if (downloadableSdkFix != null) {
      action = new UnknownInvalidSdkFixDownload(this, downloadableSdkFix);
    }

    return new UnknownInvalidSdkFix(project, this, action);
  }
}
