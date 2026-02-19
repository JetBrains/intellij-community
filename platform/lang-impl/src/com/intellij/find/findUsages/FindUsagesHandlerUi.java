// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FindUsagesHandlerUi {
  @NotNull
  AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab);


  default @Nullable String getHelpId() {
    return null;
  }
}
