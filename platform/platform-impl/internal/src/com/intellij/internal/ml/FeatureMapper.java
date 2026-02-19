// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Calculate one element of features array that will be passed into ML model
 */
@ApiStatus.Internal
public interface FeatureMapper {
  @NotNull
  String getFeatureName();

  double asArrayValue(@Nullable Object value);
}
