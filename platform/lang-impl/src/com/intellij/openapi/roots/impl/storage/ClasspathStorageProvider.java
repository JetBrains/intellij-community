// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModuleRootModel;
import org.jetbrains.annotations.*;

/**
 * This class isn't supposed to be used in external plugins.
 */
@ApiStatus.Internal
public interface ClasspathStorageProvider {
  @NonNls ExtensionPointName<ClasspathStorageProvider> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.classpathStorageProvider");

  @NonNls
  @NotNull
  String getID();

  @Nls
  @NotNull
  String getDescription();

  void assertCompatible(@NotNull ModuleRootModel model) throws ConfigurationException;

  void detach(@NotNull Module module);

  default void attach(@NotNull ModuleRootModel model) {
  }

  default void moduleRenamed(@NotNull Module module, @NotNull String oldName, @NotNull String newName) {
  }

  @Nullable
  String getContentRoot(@NotNull ModuleRootModel model);

  default void modulePathChanged(@NotNull Module module) {
  }
}
