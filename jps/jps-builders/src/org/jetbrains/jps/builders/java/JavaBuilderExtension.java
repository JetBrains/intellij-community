// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.io.File;
import java.util.Set;

/**
 * Implement this class to customize how Java files are compiled. Implementations are registered as Java services, by creating
 * a file META-INF/services/org.jetbrains.jps.builders.java.JavaBuilderExtension containing the qualified name of your implementation class.
 */
public abstract class JavaBuilderExtension {
  /**
   * @return {@code true} if encoding of {@code file} should be taken into account while computing encoding for Java compilation process
   */
  public boolean shouldHonorFileEncodingForCompilation(@NotNull File file) {
    return false;
  }

  /**
   * Override this method to extend set of modules which should be processed by Java compiler.
   */
  public @NotNull Set<? extends JpsModuleType<?>> getCompilableModuleTypes() {
    return Set.of();
  }
}
