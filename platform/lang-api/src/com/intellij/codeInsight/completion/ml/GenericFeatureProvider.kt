// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.ml

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import org.jetbrains.annotations.ApiStatus
import kotlin.system.measureTimeMillis

@ApiStatus.Internal
interface GenericFeatureProvider {
  val name: String
  fun knownFeatures(): Set<String>
  fun calculateFeatures(context: MLContext): Map<String, Any>
  fun isApplicable(context: MLContext): Boolean

  data class FeaturesCalculationResult(val features: Map<String, Any>, val performance: Map<String, Long>)

  companion object {
    private val EP_NAME = LanguageExtension<GenericFeatureProvider>("com.intellij.completion.ml.genericFeatures")

    fun forLanguage(language: Language): List<GenericFeatureProvider> = EP_NAME.allForLanguageOrAny(language)

    fun calculateFeatures(context: MLContext, features: Set<String>): FeaturesCalculationResult {
      val featureValues = mutableMapOf<String, Any>()
      val performance = mutableMapOf<String, Long>()
      for (provider in forLanguage(context.position.language)) {
        if (provider.knownFeatures().any { it in features }) {
          val time = measureTimeMillis {
            featureValues.putAll(provider.calculateFeatures(context))
          }
          performance[provider.name] = time
        }
      }
      return FeaturesCalculationResult(featureValues, performance)
    }
  }
}
