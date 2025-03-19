// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import org.jetbrains.annotations.NotNull;

final class FindPopupScopeUIProviderImpl implements FindPopupScopeUIProvider {
  @Override
  public @NotNull FindPopupScopeUI create(@NotNull FindPopupPanel findPopupPanel) {
    return new FindPopupScopeUIImpl(findPopupPanel);
  }
}
