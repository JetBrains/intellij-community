// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.cl;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;

public interface PluginAwareClassLoader {
  int ACTIVE = 1;
  int UNLOAD_IN_PROGRESS = 2;

  @NotNull PluginDescriptor getPluginDescriptor();

  @NotNull PluginId getPluginId();

  int getInstanceId();

  long getEdtTime();

  long getBackgroundTime();

  long getLoadedClassCount();

  @NotNull Collection<Path> getFiles();

  @MagicConstant(intValues = {ACTIVE, UNLOAD_IN_PROGRESS})
  int getState();

  /**
   * Loads class by name from this classloader and delegates loading to parent classloaders if and only if not found.
   */
  @Nullable Class<?> tryLoadingClass(@NotNull String name, boolean forceLoadFromSubPluginClassloader)
    throws ClassNotFoundException;

  @Nullable String getPackagePrefix();
}
