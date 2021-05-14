// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereMLStatisticsCollector.Companion.fillActionItemInfo
import com.intellij.ide.util.gotoByName.GotoActionModel
import java.util.*

class SearchEverywhereMLCache private constructor() {
  private var idCounter = 0
  private val elementIds = IdentityHashMap<Any, Int>()
  private val elementIdsToWeights = mutableMapOf<Int, Double>()
  private val model: SearchEverywhereActionsRankingModel
  
  init {
    val provider = SearchEverywhereActionsRankingModelProvider()
    model = SearchEverywhereActionsRankingModel(provider)
  }

  fun getMLWeight(element: Any, contributorId: String): Double {
    val mlId = getMLId(element)
    return elementIdsToWeights.computeIfAbsent(mlId) {
      if (element !is GotoActionModel.ActionWrapper){
        throw NotImplementedError("Not supported for objects other than ActionWrapper")
      }
      val featuresMap = fillActionItemInfo(0, System.nanoTime(), element, contributorId).toMap()  
      model.predict(featuresMap)
    }
  }

  fun getMLId(element: Any): Int {
    return elementIds.computeIfAbsent(element) { idCounter++ }
  }

  companion object {
    private val sessionIdsToCaches = mutableMapOf<Int, SearchEverywhereMLCache>()
    @JvmStatic
    fun getCache(sessionId: Int): SearchEverywhereMLCache {
      return sessionIdsToCaches.computeIfAbsent(sessionId) { SearchEverywhereMLCache() }
    }

    @JvmStatic
    fun removeCache(sessionId: Int): SearchEverywhereMLCache? {
      return sessionIdsToCaches.remove(sessionId)
    }
  }
}