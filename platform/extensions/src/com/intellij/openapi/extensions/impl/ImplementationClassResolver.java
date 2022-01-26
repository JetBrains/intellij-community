// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
interface ImplementationClassResolver {
  @NotNull Class<?> resolveImplementationClass(@NotNull ComponentManager componentManager, @NotNull ExtensionComponentAdapter adapter)
    throws ClassNotFoundException;
}
