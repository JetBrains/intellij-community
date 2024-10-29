// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
@ApiStatus.Internal
public interface FindPopupScopeUIProvider {
  static FindPopupScopeUIProvider getInstance() {
    return ApplicationManager.getApplication().getService(FindPopupScopeUIProvider.class);
  }

  @NotNull
  FindPopupScopeUI create(@NotNull FindPopupPanel findPopupPanel);
}

