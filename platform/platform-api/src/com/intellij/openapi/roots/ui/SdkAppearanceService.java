// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SdkAppearanceService {
  public abstract @NotNull CellAppearanceEx forSdk(@NotNull SdkTypeId sdkType,
                                                   @NlsSafe @NotNull String name,
                                                   @Nullable String versionString,
                                                   boolean hasValidPath,
                                                   boolean isInComboBox,
                                                   boolean selected);

  public static @NotNull SdkAppearanceService getInstance() {
    return ApplicationManager.getApplication().getService(SdkAppearanceService.class);
  }

  public abstract @NotNull CellAppearanceEx forNullSdk(boolean selected);

  public abstract @NotNull CellAppearanceEx forSdk(@Nullable Sdk sdk, boolean isInComboBox, boolean selected, boolean showVersion);
}
