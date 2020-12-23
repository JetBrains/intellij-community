// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public abstract Class<?> @NotNull [] getPatternClasses(@Nullable final String alias);

  @NotNull
  public abstract <T> PatternCompiler<T> getPatternCompiler(Class @NotNull [] patternClasses);

  @NotNull
  public <T> PatternCompiler<T> getPatternCompiler(@Nullable final String alias) {
    return getPatternCompiler(getPatternClasses(alias));
  }

  public abstract void dropCache();
}
