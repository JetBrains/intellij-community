// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores predefined and custom (user-defined) path variables. Path variables are used to convert paths from absolute to portable form and
 * vice versa. It allows us to reuse project configuration files on different machines.
 * <p>
 * In order to make a path (or URL) portable the serialization subsystem replaces its prefix by name of a corresponding path variable.
 * There are {@link #getSystemMacroNames() predefined path variables} and also it's possible to specify {@link #getUserMacroNames() custom path variables}.
 * </p>
 */
public abstract class PathMacros {
  public static PathMacros getInstance() {
    return ApplicationManager.getApplication().getService(PathMacros.class);
  }

  public abstract @NotNull Set<String> getAllMacroNames();

  public abstract @Nullable String getValue(@NotNull String name);

  /**
   * Consider using {@link PathMacroContributor}.
   */
  @ApiStatus.Internal
  public abstract void setMacro(@NotNull String name, @Nullable String value);

  public abstract @NotNull Set<String> getUserMacroNames();

  public abstract @NotNull Map<String, String> getUserMacros();

  public abstract @NotNull Set<String> getSystemMacroNames();

  public abstract @NotNull Collection<String> getIgnoredMacroNames();

  public abstract void setIgnoredMacroNames(final @NotNull Collection<String> names);

  public abstract void addIgnoredMacro(@NotNull List<String> names);

  public abstract boolean isIgnoredMacroName(@NotNull String macro);

  public abstract void removeAllMacros();

  public abstract @NotNull Collection<String> getLegacyMacroNames();
}
