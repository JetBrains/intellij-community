// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns.compiler;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public abstract class PatternCompilerFactory {
  public static PatternCompilerFactory getFactory() {
    return ApplicationManager.getApplication().getService(PatternCompilerFactory.class);
  }

  /**
   * Retrieves pattern classes registered via com.intellij.patterns.patternClass extension.
   * @param alias or null
   * @return pattern classes
   */
  public abstract Class<?> @NotNull [] getPatternClasses(@Nullable String alias);

  public abstract @NotNull <T> PatternCompiler<T> getPatternCompiler(Class @NotNull [] patternClasses);

  public @NotNull <T> PatternCompiler<T> getPatternCompiler(final @Nullable String alias) {
    return getPatternCompiler(getPatternClasses(alias));
  }

  public abstract void dropCache();
}
