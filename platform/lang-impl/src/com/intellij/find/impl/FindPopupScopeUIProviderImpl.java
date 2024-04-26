// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import org.jetbrains.annotations.NotNull;

public final class FindPopupScopeUIProviderImpl implements FindPopupScopeUIProvider {
  @NotNull
  @Override
  public FindPopupScopeUI create(@NotNull FindPopupPanel findPopupPanel) {
    return new FindPopupScopeUIImpl(findPopupPanel);
  }
}
