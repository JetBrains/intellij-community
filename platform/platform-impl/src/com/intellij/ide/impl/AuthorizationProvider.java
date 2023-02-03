// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface AuthorizationProvider {
  ExtensionPointName<AuthorizationProvider> EP_NAME = new ExtensionPointName<>("com.intellij.authorizationProvider");

  @Nullable
  String getAccessToken(@NotNull String url);
}
