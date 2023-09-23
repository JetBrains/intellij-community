// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.io.File;
import java.util.Collections;
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
    return Collections.emptySet();
  }

  /**
   * @deprecated is not called anymore from dependency analysis, as the constant information is obtained directly from javac's AST
   *
   * Override this method to provide additional constant search capabilities that would augment the logic already built into the java builder
   * Results from ConstantAffectionResolver extensions will be combined with the results found by the java ConstantAffectionResolver.
   * The implementation should expect asynchronous execution.
   */
  @Deprecated(forRemoval = true)
  public @Nullable Callbacks.ConstantAffectionResolver getConstantSearch(CompileContext context) {
    return null;
  }
}
