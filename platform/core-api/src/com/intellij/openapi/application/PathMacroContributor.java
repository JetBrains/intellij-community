// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * <p>
 *   Application-wide Path Macro contributor.
 * </p>
 * <p>
 *   See {@link com.intellij.openapi.components.impl.ProjectWidePathMacroContributor} for the project-wide analog
 * </p>
 */
public interface PathMacroContributor {
  /**
   * Register path.macros
   *
   * Note: Value will be overridden if key is specified in <code>path.macros.xml</code>.
   */
  void registerPathMacros(@NotNull Map<String, String> macros, @NotNull Map<String, String> legacyMacros);

  /**
   * Register path.macros even if key is specified in <code>path.macros.xml</code>.
   */
  default void forceRegisterPathMacros(@NotNull Map<String, String> macros) {}
}
