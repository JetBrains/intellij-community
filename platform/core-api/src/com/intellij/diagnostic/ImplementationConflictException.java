// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class ImplementationConflictException extends RuntimeException {
  @NotNull
  private final Set<PluginId> myConflictingPluginIds = new HashSet<>();
  private boolean myConflictWithPlatform;

  public ImplementationConflictException(String message, Throwable cause, Object @NotNull ... implementationObjects) {
    super(message, cause);

    for (Object object : implementationObjects) {
      final ClassLoader classLoader = object.getClass().getClassLoader();
      if (classLoader instanceof PluginAwareClassLoader) {
        myConflictingPluginIds.add(((PluginAwareClassLoader)classLoader).getPluginId());
      }
      else {
        myConflictWithPlatform = true;
      }
    }
  }

  public @NotNull Set<PluginId> getConflictingPluginIds() {
    return myConflictingPluginIds;
  }

  public boolean isConflictWithPlatform() {
    return myConflictWithPlatform;
  }
}
