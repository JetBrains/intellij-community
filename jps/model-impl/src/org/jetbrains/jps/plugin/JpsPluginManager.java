// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.plugin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Collection;

/**
 * This class is supposed to be used in the implementation of {@link JpsServiceManager} only. Other code must use {@link JpsServiceManager}
 * instead.
 */
@ApiStatus.Internal
public abstract class JpsPluginManager {
  public static @NotNull JpsPluginManager getInstance() {
    return JpsServiceManager.getInstance().getService(JpsPluginManager.class);
  }

  public abstract @NotNull <T> Collection<T> loadExtensions(@NotNull Class<T> extensionClass);

  public abstract boolean isFullyLoaded();

  public abstract int getModificationStamp();
}
