// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ml.catboost

import com.intellij.internal.ml.ModelMetadataReader
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
@IntellijInternalApi
interface CatBoostModelMetadataReader : ModelMetadataReader {
  fun loadModel(): NaiveCatBoostModel
}
