// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public interface DifferentiateParameters {

  boolean isCalculateAffected();

  boolean isProcessConstantsIncrementally();

  @NotNull
  Predicate<? super NodeSource> affectionFilter();

  @NotNull
  Predicate<? super NodeSource> belongsToCurrentCompilationChunk();
}
