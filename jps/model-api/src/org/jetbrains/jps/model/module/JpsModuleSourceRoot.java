// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

public interface JpsModuleSourceRoot extends JpsElement {
  @NotNull JpsModuleSourceRootType<?> getRootType();

  /**
   * @return the root properties element or {@code null} if the root type doesn't equal to {@code type}
   */
  @Nullable <P extends JpsElement> P getProperties(@NotNull JpsModuleSourceRootType<P> type);

  /**
   * @return the root properties element or {@code null} if the root type isn't contained in {@code types}
   */
  @Nullable <P extends JpsElement> P getProperties(@NotNull Set<? extends JpsModuleSourceRootType<P>> types);

  @Nullable <P extends JpsElement> JpsTypedModuleSourceRoot<P> asTyped(@NotNull JpsModuleSourceRootType<P> type);

  @NotNull JpsTypedModuleSourceRoot<?> asTyped();

  @NotNull JpsElement getProperties();

  @NotNull String getUrl();

  @NotNull File getFile();

  @NotNull Path getPath();
}
