// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Represents ML model that can predict some values by given array of independent variables
 */
@ApiStatus.Internal
public interface DecisionFunction {
  FeatureMapper @NotNull [] getFeaturesOrder();

  @NotNull
  List<String> getRequiredFeatures();

  @NotNull
  List<String> getUnknownFeatures(@NotNull Collection<String> features);

  @Nullable
  @NonNls
  String version();

  double predict(double[] features);
}
