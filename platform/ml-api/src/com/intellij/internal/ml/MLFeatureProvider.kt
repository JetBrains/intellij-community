// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ml

import com.intellij.lang.LanguageExtension
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MLFeatureProvider {
  val name: String
  fun knownFeatures(): Set<String>
  fun calculateFeatures(context: MLContext): List<MLFeature>
  fun isApplicable(context: MLContext): Boolean

  companion object {
    private val EP_NAME = LanguageExtension<MLFeatureProvider>("com.intellij.internal.ml.featureProvider")

    fun applicableProviders(context: MLContext): List<MLFeatureProvider> =
      EP_NAME.allForLanguageOrAny(context.position.language).filter {
        it.isApplicable(context) }
  }
}
