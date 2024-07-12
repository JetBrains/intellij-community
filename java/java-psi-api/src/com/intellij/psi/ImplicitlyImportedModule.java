// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a module that is implicitly imported.
 *
 */
@ApiStatus.Experimental
public final class ImplicitlyImportedModule {
  public static final @NotNull ImplicitlyImportedModule @NotNull [] EMPTY_ARRAY = new ImplicitlyImportedModule[0];

  private final @NotNull String myModuleName;

  private ImplicitlyImportedModule(@NotNull String moduleName) {
    myModuleName = moduleName;
  }

  public @NotNull String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public static ImplicitlyImportedModule create(@NotNull String moduleName) {
    return new ImplicitlyImportedModule(moduleName);
  }
}
