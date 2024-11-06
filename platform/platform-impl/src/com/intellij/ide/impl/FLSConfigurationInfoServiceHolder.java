// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import org.jetbrains.annotations.ApiStatus;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

@ApiStatus.Internal
final class FLSConfigurationInfoServiceHolder {

  static final FLSConfigurationInfoService INSTANCE;

  static {
    FLSConfigurationInfoService service;
    try {
      service = ServiceLoader.load(FLSConfigurationInfoService.class, FLSConfigurationInfoService.class.getClassLoader()).findFirst().orElse(null);
    }
    catch (ServiceConfigurationError ignored) {
      service = null;
    }
    INSTANCE = service;
  }
}