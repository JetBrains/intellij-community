// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.ResourcesModelMetadataReader
import com.intellij.searchEverywhere.model.PredictionModel


class SearchEverywhereActionsRankingModelProvider(private val resourceDirectory: String = "features") : SearchEverywhereMLRankingModelProvider {
  override val model: DecisionFunction
    get() {
      val metadata = FeaturesInfo.buildInfo(ResourcesModelMetadataReader(this::class.java, resourceDirectory))
      return object : SearchEverywhereMLRankingDecisionFunction(metadata) {
        override fun predict(features: DoubleArray?): Double = PredictionModel.makePredict(features)
      }
    }

  override val displayNameInSettings: String
    get() = IdeBundle.message("searcheverywhere.ml.actions.display.name.in.settings")

  override fun isContributorSupported(contributor: SearchEverywhereContributor<*>): Boolean {
    return contributor is ActionSearchEverywhereContributor
  }
}