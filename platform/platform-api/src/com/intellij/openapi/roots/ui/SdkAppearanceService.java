// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SdkAppearanceService {
  @NotNull
  public abstract CellAppearanceEx forSdk(@NotNull SdkTypeId sdkType,
                                          @NlsSafe @NotNull String name,
                                          @Nullable String versionString,
                                          boolean hasValidPath,
                                          boolean isInComboBox,
                                          boolean selected);

  @NotNull
  public static SdkAppearanceService getInstance() {
    return ApplicationManager.getApplication().getService(SdkAppearanceService.class);
  }

  @NotNull
  public abstract CellAppearanceEx forNullSdk(boolean selected);

  @NotNull
  public abstract CellAppearanceEx forSdk(@Nullable Sdk sdk, boolean isInComboBox, boolean selected, boolean showVersion);
}
