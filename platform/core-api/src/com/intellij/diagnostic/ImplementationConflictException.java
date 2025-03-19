// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ImplementationConflictException extends RuntimeException {
  private static final @NotNull PluginId CORE_PLUGIN_ID = PluginId.getId("com.intellij");
  private final @NotNull Set<PluginId> myConflictingPluginIds;
  public ImplementationConflictException(@NotNull String message, Throwable cause, Object @NotNull ... implementationObjects) {
    super(message + ". Conflicting plugins: "+calculateConflicts(implementationObjects), cause);
    myConflictingPluginIds = calculateConflicts(implementationObjects);
  }

  private static @NotNull Set<PluginId> calculateConflicts(Object @NotNull ... implementationObjects) {
    Set<PluginId> myConflictingPluginIds = new HashSet<>();
    for (Object object : implementationObjects) {
      final ClassLoader classLoader = object.getClass().getClassLoader();
      myConflictingPluginIds.add(PluginUtil.getPluginId(classLoader));
    }
    return myConflictingPluginIds;
  }

  public @NotNull Set<PluginId> getConflictingPluginIds() {
    return new HashSet<>(ContainerUtil.subtract(myConflictingPluginIds, Collections.singleton(CORE_PLUGIN_ID)));
  }

  public boolean isConflictWithPlatform() {
    return myConflictingPluginIds.contains(CORE_PLUGIN_ID);
  }
}
