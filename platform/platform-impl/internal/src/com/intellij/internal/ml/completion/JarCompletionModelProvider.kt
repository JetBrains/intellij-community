// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml.completion

import com.intellij.internal.ml.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
abstract class JarCompletionModelProvider(@Nls(capitalization = Nls.Capitalization.Title) private val displayName: String,
                                          @NonNls private val resourceDirectory: String) : RankingModelProvider {
  private val lazyModel: DecisionFunction by lazy {
    val metadata = FeaturesInfo.buildInfo(ResourcesModelMetadataReader(this::class.java, resourceDirectory))
    createModel(metadata)
  }

  override fun getModel(): DecisionFunction = lazyModel

  override fun getDisplayNameInSettings(): String = displayName

  protected abstract fun createModel(metadata: ModelMetadata): DecisionFunction

  @TestOnly
  fun assertModelMetadataConsistent() {
    try {
      val decisionFunction = model
      decisionFunction.version()

      val unknownRequiredFeatures = decisionFunction.getUnknownFeatures(decisionFunction.requiredFeatures)
      assert(unknownRequiredFeatures.isEmpty()) { "All required features should be known, but $unknownRequiredFeatures unknown" }

      val featuresOrder = decisionFunction.featuresOrder
      val unknownUsedFeatures = decisionFunction.getUnknownFeatures(featuresOrder.map { it.featureName }.distinct())
      assert(unknownUsedFeatures.isEmpty()) { "All used features should be known, but $unknownUsedFeatures unknown" }

      val features = DoubleArray(featuresOrder.size)
      decisionFunction.predict(features)
    }
    catch (e: InconsistentMetadataException) {
      throw AssertionError("Model metadata inconsistent", e)
    }
  }
}
