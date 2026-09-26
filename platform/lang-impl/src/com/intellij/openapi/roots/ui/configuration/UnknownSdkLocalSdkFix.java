// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Locally detected SDK to fix immediately
 */
public interface UnknownSdkLocalSdkFix extends UnknownSdkFixConfigurator {
  /**
   * @return the resolved home of the detected SDK to configure
   */
  @NotNull
  String getExistingSdkHome();

  /**
   * @return the actual version string of the SDK,
   * it is used for {@link com.intellij.openapi.projectRoots.SdkModificator#setVersionString(String)}
   * and should be similar to what the respective {@link com.intellij.openapi.projectRoots.SdkType}
   * configures in {@link com.intellij.openapi.projectRoots.SdkType#setupSdkPaths(Sdk)}
   * @see #getPresentableVersionString()
   */
  @NotNull String getVersionString();

  /**
   * @return version string that is short and enough to be shown in UI
   * @see #getVersionString()
   */
  default @NotNull String getPresentableVersionString() {
    return getVersionString();
  }

  /**
   * @return suggested name for an SDK to be created, still, the name could
   * be altered to avoid conflicts
   */
  @NotNull
  String getSuggestedSdkName();

  /**
   * A suggestion can be using another already registered {@link Sdk} as prototype,
   * The callee may use this to avoid creating duplicates
   */
  default @Nullable Sdk getRegisteredSdkPrototype() {
    return null;
  }
}
