// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated do not use module services, use <a href="https://plugins.jetbrains.com/docs/intellij/plugin-services.html">other kinds of services</a>
 * instead
 */
@Deprecated
public final class ModuleServiceManager {
  private ModuleServiceManager() {
  }

  public static @Nullable <T> T getService(@NotNull Module module, @NotNull Class<T> serviceClass) {
    return module.getService(serviceClass);
  }
}