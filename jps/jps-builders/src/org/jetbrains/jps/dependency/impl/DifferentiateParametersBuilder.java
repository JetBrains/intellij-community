// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.DifferentiateParameters;
import org.jetbrains.jps.dependency.NodeSource;

import java.util.function.Predicate;

public final class DifferentiateParametersBuilder implements DifferentiateParameters {
  private boolean calculateAffected = true;
  private boolean processConstantsIncrementally = true;
  private Predicate<? super NodeSource> myAffectionFilter = s -> true;
  private Predicate<? super NodeSource> myCurrentChunkFilter = s -> true;

  private DifferentiateParametersBuilder() {
  }

  @Override
  public boolean isCalculateAffected() {
    return calculateAffected;
  }

  @Override
  public boolean isProcessConstantsIncrementally() {
    return processConstantsIncrementally;
  }

  @Override
  public @NotNull Predicate<? super NodeSource> affectionFilter() {
    return myAffectionFilter;
  }

  @Override
  public @NotNull Predicate<? super NodeSource> belongsToCurrentCompilationChunk() {
    return myCurrentChunkFilter;
  }

  public DifferentiateParameters get() {
    return this;
  }

  public static DifferentiateParametersBuilder create() {
    return new DifferentiateParametersBuilder();
  }

  public static DifferentiateParameters withDefaultSettings() {
    return create().get();
  }

  public DifferentiateParametersBuilder calculateAffected(boolean value) {
    calculateAffected = value;
    return this;
  }

  public DifferentiateParametersBuilder processConstantsIncrementally(boolean value) {
    processConstantsIncrementally = value;
    return this;
  }

  public DifferentiateParametersBuilder withAffectionFilter(Predicate<? super NodeSource> filter) {
    myAffectionFilter = filter;
    return this;
  }

  public DifferentiateParametersBuilder withChunkStructureFilter(Predicate<? super NodeSource> filter) {
    myCurrentChunkFilter = filter;
    return this;
  }
}
