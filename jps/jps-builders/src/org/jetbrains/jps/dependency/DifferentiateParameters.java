// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public interface DifferentiateParameters {
  default String getSessionName() {
    return "";
  }

  boolean isCalculateAffected();

  boolean isProcessConstantsIncrementally();

  /**
   * @return true, if compiler reported compilation errors while compiling a set of Delta's base sources
   * (=> the compiler might fail to produce any output for these sources and therefore no nodes were registered for such sources).
   * In this case nodes, corresponding to Delta's base sources will be treated differently: for example, they won't be automatically considered as "deleted"
   */
  boolean isCompiledWithErrors();

  @NotNull
  Predicate<? super NodeSource> affectionFilter();

  @NotNull
  Predicate<? super NodeSource> belongsToCurrentCompilationChunk();

  @NotNull
  static Predicate<? super NodeSource> affectableInCurrentChunk(DifferentiateParameters params) {
    var inCurrentChunk = params.belongsToCurrentCompilationChunk();
    var affectable = params.affectionFilter();
    return s -> inCurrentChunk.test(s) && affectable.test(s);
  }

}
