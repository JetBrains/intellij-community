// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaCompilingTool;

import java.util.Collection;
import java.util.Collections;

/**
 * An extension for setting up additional VM options for external java compiler.
 */
public interface ExternalJavacOptionsProvider {
  /**
   * @deprecated Use {@link #getOptions(JavaCompilingTool, int)}
   */
  @Deprecated(forRemoval = true)
  default @NotNull Collection<String> getOptions(@NotNull JavaCompilingTool tool) {
    return Collections.emptyList();
  }

  default @NotNull Collection<String> getOptions(@NotNull JavaCompilingTool tool, int compilerSdkVersion) {
    return getOptions(tool);
  }
}

