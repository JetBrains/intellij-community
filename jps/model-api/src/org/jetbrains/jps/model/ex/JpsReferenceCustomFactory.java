// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * This is an internal API used to create custom implementation of {@link org.jetbrains.jps.model.JpsElementReference} if the implementation
 * of JPS Model based on the workspace model is used.
 */
@ApiStatus.Internal
public interface JpsReferenceCustomFactory {
  boolean isEnabled();

  @NotNull JpsModuleReference createModuleReference(@NotNull String moduleName);

  @NotNull JpsLibraryReference createLibraryReference(@NotNull String libraryName,
                                                      @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference);
}
