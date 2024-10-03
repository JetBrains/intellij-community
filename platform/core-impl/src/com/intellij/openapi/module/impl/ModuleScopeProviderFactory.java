// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;

/**
 * Creates module scope provider {@link com.intellij.openapi.module.impl.ModuleScopeProvider} from module.
 * The default implementation may be changed in your IDE by overriding this service.
 */
@ApiStatus.Internal
public interface ModuleScopeProviderFactory {
  @NotNull
  ModuleScopeProvider createProvider(@NotNull Module module);
}
