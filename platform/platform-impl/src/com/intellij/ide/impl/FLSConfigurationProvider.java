// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface FLSConfigurationProvider {

  ExtensionPointName<FLSConfigurationProvider> EP_NAME = new ExtensionPointName<>("com.intellij.flsConfigurationProvider");

  /**
   * Provides an FLS url to be used to get a license ticket from
   * @return url of the license server to use or null otherwise
   */
  @Nullable
  String getUrl();
}
