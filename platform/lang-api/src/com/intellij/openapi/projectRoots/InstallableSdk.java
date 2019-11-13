// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a placeholder of a real Sdk, e.g. for an Sdk that will be downloaded (or is downloading)
 * @see SdkType#supportsCustomDownloadUI()
 */
public abstract class InstallableSdk extends UserDataHolderBase implements Sdk {
  /**
   * Executed under progress in the background thread to materialize
   * this LazySdk into a real SDK, for example by downloading and unpacking it
   *
   * Exception message will be used in the UI to show the error info
   *
   * @param indicator cancellable progress indicator
   *
   * @return ready to go SDK
   *
   * //TODO: is it better to just return the SdkHome and let standard implementation handle it?
   */
  @NotNull
  public abstract Sdk prepareSdk(@NotNull ProgressIndicator indicator);

  //Do we need an on-set-up listener here?

  @Nullable
  @Override
  public final String getHomePath() {
    return null;
  }

  @Nullable
  @Override
  public final VirtualFile getHomeDirectory() {
    return null;
  }

  @NotNull
  @Override
  public SdkModificator getSdkModificator() {
    throw new IllegalStateException("InstallableSdk does not support Modifications");
  }

  @NotNull
  @Override
  public final RootProvider getRootProvider() {
    throw new IllegalStateException("InstallableSdk must not be used outside of the editor UI");
  }

  @Nullable
  @Override
  public SdkAdditionalData getSdkAdditionalData() {
    return null;
  }

  @NotNull
  @Override
  public abstract InstallableSdk clone();
}
