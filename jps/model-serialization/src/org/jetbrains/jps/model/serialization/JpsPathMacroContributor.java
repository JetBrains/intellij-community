// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * <p>
 *   This is an alternative Path Macro contributor specifically for JPS.
 * </p>
 * <p>
 *   Note: all path macros contributed by {@link com.intellij.openapi.application.PathMacroContributor} are already handled by the JPS.
 *   Contrary, path macros contributed by {@link com.intellij.openapi.components.impl.ProjectWidePathMacroContributor} are not
 *   automatically handled by the JPS so in such cases you may want to use this {@link JpsPathMacroContributor}
 * </p>
 *
 * @see com.intellij.openapi.components.impl.ProjectWidePathMacroContributor
 */
public interface JpsPathMacroContributor {
  /**
   * You are supposed to pass path macros into JPS process via System properties. You can pass custom
   * System properties to the JPS process using {@link com.intellij.compiler.server.BuildProcessParametersProvider}
   */
  @NotNull Map<@NotNull String, @NotNull String> getPathMacros();
}
