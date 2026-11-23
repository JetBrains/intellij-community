// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer;

import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** A lazy extension bean that gives the module that declares it. */
@ApiStatus.Internal
public interface PluginAwareLazyExtension {
  @NotNull PluginDescriptor getPluginDescriptor();
}
