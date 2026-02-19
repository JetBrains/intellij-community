// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.cl;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import kotlinx.coroutines.CoroutineScope;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.Collection;

@NonExtendable
public interface PluginAwareClassLoader {
  @Internal
  int ACTIVE = 1;
  @Internal
  int UNLOAD_IN_PROGRESS = 2;

  @NotNull PluginDescriptor getPluginDescriptor();

  @NotNull PluginId getPluginId();

  @Internal
  @Nullable String getModuleId();

  @Internal
  long getEdtTime();

  @Internal
  long getBackgroundTime();

  @Internal
  long getLoadedClassCount();

  @Internal
  @NotNull @Unmodifiable
  Collection<Path> getFiles();

  @Internal
  @MagicConstant(intValues = {ACTIVE, UNLOAD_IN_PROGRESS})
  int getState();

  /**
   * Loads class by name from this classloader and delegates loading to parent classloaders if and only if not found.
   */
  @Internal
  @Nullable Class<?> tryLoadingClass(@NotNull String name, boolean forceLoadFromSubPluginClassloader) throws ClassNotFoundException;

  @Internal
  @Nullable Class<?> loadClassInsideSelf(@NotNull String name) throws ClassNotFoundException;

  @Internal
  @Nullable String getPackagePrefix();

  @Internal
  @NotNull CoroutineScope getPluginCoroutineScope();
}
