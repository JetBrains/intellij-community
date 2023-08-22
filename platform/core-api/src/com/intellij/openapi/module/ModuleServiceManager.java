// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated do not use module services, use <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_services.html">other kinds of services</a>
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