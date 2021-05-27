// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereMLStatisticsCollector
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereMLStatisticsCollector.Companion.buildCommonFeaturesMap
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereMLStatisticsCollector.Companion.fillActionItemInfo
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.project.Project
import java.util.*

class SearchEverywhereMLCache private constructor(val seSessionId: Int) {
  val listVersions: MutableList<Pair<Int, List<Int>>> = ArrayList()
  private var idCounter = 0
  private val elementIds = IdentityHashMap<Any, Int>()
  private val elementIdsToWeights = mutableMapOf<Int, Double>()
  private val model: SearchEverywhereActionsRankingModel

  init {
    val provider = SearchEverywhereActionsRankingModelProvider()
    model = SearchEverywhereActionsRankingModel(provider)
  }

  @Suppress("UNCHECKED_CAST")
  fun getMLWeight(element: Any, contributorId: String, project: Project?, seTabId: String): Double {
    val mlId = getMLId(element)
    return elementIdsToWeights.computeIfAbsent(mlId) {
      if (element !is GotoActionModel.MatchedValue) {
        throw NotImplementedError("Not supported for objects other than GotoActionModel.MatchedValue")
      }
      val features = mutableMapOf<String, Any>()
      features.putAll(buildCommonFeaturesMap(seSessionId, intArrayOf(), false, -1, -1, -1, -1, seTabId, project))
      val itemInfo = fillActionItemInfo(element.matchingDegree, System.nanoTime(), element, contributorId)
      features.putAll(itemInfo.additionalData)

      itemInfo.id?.let {
        features.put(SearchEverywhereMLStatisticsCollector.ACTION_ID_KEY, it)
      }
      model.predict(features)
    }
  }

  fun getMLId(element: Any): Int {
    return elementIds.computeIfAbsent(element) { idCounter++ }
  }

  fun listRebuilt(patternLength: Int, infos: List<SearchEverywhereFoundElementInfo>) {
    listVersions.add(patternLength to infos.map {
      val obj = ((it.element as? GotoActionModel.MatchedValue)?.value as? GotoActionModel.ActionWrapper)?.action ?: it
      getMLId(obj)
    })
  }

  companion object {
    private val sessionIdsToCaches = mutableMapOf<Int, SearchEverywhereMLCache>()

    @JvmStatic
    fun getCache(sessionId: Int): SearchEverywhereMLCache {
      return sessionIdsToCaches.computeIfAbsent(sessionId) { SearchEverywhereMLCache(sessionId) }
    }

    @JvmStatic
    fun removeCache(sessionId: Int): SearchEverywhereMLCache? {
      return sessionIdsToCaches.remove(sessionId)
    }
  }
}