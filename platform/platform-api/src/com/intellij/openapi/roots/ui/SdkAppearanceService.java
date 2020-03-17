// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SdkAppearanceService {
  @NotNull
  public static SdkAppearanceService getInstance() {
    return ServiceManager.getService(SdkAppearanceService.class);
  }

  @NotNull
  public abstract CellAppearanceEx forSdk(@Nullable Sdk sdk, boolean isInComboBox, boolean selected, boolean showVersion);
}
