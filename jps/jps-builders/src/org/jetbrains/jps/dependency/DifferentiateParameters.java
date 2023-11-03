// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

public final class DifferentiateParameters {
  private final boolean calculateAffected;
  private final boolean processConstantsIncrementally;

  public DifferentiateParameters() {
    this(true, true);
  }

  public boolean isCalculateAffected() {
    return calculateAffected;
  }

  public boolean isProcessConstantsIncrementally() {
    return processConstantsIncrementally;
  }

  public DifferentiateParameters(boolean calculateAffected, boolean processConstantsIncrementally) {
    this.calculateAffected = calculateAffected;
    this.processConstantsIncrementally = processConstantsIncrementally;
  }

  public static DifferentiateParameters byDefault() {
    return new DifferentiateParameters();
  }

  public static DifferentiateParameters withoutAffectedCalculation() {
    return new DifferentiateParameters(false, true);
  }

  public static DifferentiateParameters processConstantsNonIncremental() {
    return new DifferentiateParameters(true, false);
  }
}
