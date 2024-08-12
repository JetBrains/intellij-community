// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A download suggestion to fix a missing SDK by downloading it
 * @see com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
 */
public interface UnknownSdkDownloadableSdkFix extends UnknownSdkFixConfigurator {
  /** User visible description of the proposed download **/
  @NotNull
  String getDownloadDescription();

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
   * @return reason why a SDK lookup was initiated
   */
  default @Nullable @Nls String getSdkLookupReason() {
    return null;
  }

  /**
   * Creates and SDK download task to apply the fix.
   * @see com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
   * @see com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
   */
  @NotNull
  SdkDownloadTask createTask(@NotNull ProgressIndicator indicator);
}
