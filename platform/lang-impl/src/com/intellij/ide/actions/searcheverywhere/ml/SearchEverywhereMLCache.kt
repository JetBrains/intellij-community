// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMLStatisticsCollector.Companion.ML_WEIGHT_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereActionsRankingModel
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereActionsRankingModelProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.util.*

class SearchEverywhereMLCache internal constructor(val seSessionId: Int) {
  private var idCounter = 0
  private val elementIds = IdentityHashMap<Any, Int>()
  private val elementIdsToFeatures = mutableMapOf<Int, Map<String, Any>>()
  private val model: SearchEverywhereActionsRankingModel
  private lateinit var lastToolwindowId: String //bound to the current session

  init {
    val provider = SearchEverywhereActionsRankingModelProvider()
    model = SearchEverywhereActionsRankingModel(provider)
  }

  @Suppress("UNCHECKED_CAST")
  fun getMLWeight(element: Any, contributor: SearchEverywhereContributor<*>, project: Project?, patternLength: Int): Double {
    val mlId = getMLId(element)
    return elementIdsToFeatures.computeIfAbsent(mlId) {
      computeWeight(element, contributor, project, patternLength)
    }[ML_WEIGHT_KEY] as Double
  }

  fun getFeatures(element: Any): Map<String, Any>? {
    val id = elementIds[element] ?: return null
    return elementIdsToFeatures[id]
  }

  fun getMLId(element: Any): Int {
    return elementIds.computeIfAbsent(element) { idCounter++ }
  }

  private fun computeWeight(element: Any,
                            contributor: SearchEverywhereContributor<*>,
                            project: Project?,
                            patternLength: Int): MutableMap<String, Any> {
    if (element !is GotoActionModel.MatchedValue) {
      throw NotImplementedError("Not supported for objects other than GotoActionModel.MatchedValue")
    }

    val features = mutableMapOf<String, Any>()
    val contextFeaturesProvider = SearchEverywhereFeaturesProvider.getContextFeaturesProvider()
    val elementFeaturesProvider = SearchEverywhereFeaturesProvider.getElementFeatureProvider()
    features.putAll(contextFeaturesProvider.getContextFeatures(project, getLastTWId(project), patternLength))
    val itemInfo = elementFeaturesProvider.getElementFeatures(element.matchingDegree, element, contributor, System.currentTimeMillis())
    features.putAll(itemInfo.additionalData)
    features[ML_WEIGHT_KEY] = model.predict(features)
    return features
  }

  private fun getLastTWId(project: Project?): String? {
    if (!this::lastToolwindowId.isInitialized) {
      lastToolwindowId = if (project != null) {
        val twm = ToolWindowManager.getInstance(project)
        var id: String? = null
        ApplicationManager.getApplication().invokeAndWait {
          id = twm.lastActiveToolWindowId
        }
        id ?: ""
      }
      else {
        ""
      }
    }
    return lastToolwindowId.ifEmpty { null }
  }
}