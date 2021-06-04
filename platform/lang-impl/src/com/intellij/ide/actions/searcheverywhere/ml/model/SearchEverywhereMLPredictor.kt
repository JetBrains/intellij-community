// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMLCache
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereSearchState
import com.intellij.ide.util.gotoByName.GotoActionModel

internal class SearchEverywhereMLPredictor {
  private val model: SearchEverywhereActionsRankingModel

  init {
    val provider = SearchEverywhereActionsRankingModelProvider()
    model = SearchEverywhereActionsRankingModel(provider)
  }

  fun predictMLWeight(element: Any,
                      contributor: SearchEverywhereContributor<*>,
                      storage: SearchEverywhereMLCache,
                      state: SearchEverywhereSearchState?): Double {
    if (element !is GotoActionModel.MatchedValue) {
      throw NotImplementedError("Not supported for objects other than GotoActionModel.MatchedValue")
    }

    val features = mutableMapOf<String, Any>()
    features.putAll(storage.getContextFeatures().features)
    features.putAll(storage.getElementFeatures(element, contributor, state).features)
    return model.predict(features)
  }
}