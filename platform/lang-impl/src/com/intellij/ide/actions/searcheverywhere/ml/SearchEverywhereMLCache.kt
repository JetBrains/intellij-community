// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereActionFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContextFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereMLPredictor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.containers.ObjectIntHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class SearchEverywhereMLCache internal constructor(project: Project?) {
  private val startTime: Long = System.currentTimeMillis()
  private var idCounter = AtomicInteger(1)
  private val elementIds = ObjectIntHashMap<Any>()

  // context features are calculated once per Search Everywhere session
  private val cachedContextInfo: SearchEverywhereMLContextInfo by lazy { initContextInfo(project) }

  // element features & ML score are re-calculated on each typing because some of them might change (e.g. matching degree)
  private val cachedElementsInfo = mutableMapOf<Int, SearchEverywhereMLItemInfo>()
  private val cachedMLWeight = mutableMapOf<Int, Double>()

  private val predictor = SearchEverywhereMLPredictor()
  private val localSummary: ActionsLocalSummary by lazy { ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java) }
  private val globalSummary: ActionsGlobalSummaryManager by lazy { ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java) }

  @Synchronized
  fun getContextFeatures(): SearchEverywhereMLContextInfo {
    return cachedContextInfo
  }

  @Synchronized
  fun getElementFeatures(element: GotoActionModel.MatchedValue,
                         contributor: SearchEverywhereContributor<*>,
                         state: SearchEverywhereSearchState?): SearchEverywhereMLItemInfo {
    val id = getMLId(element)
    return cachedElementsInfo.computeIfAbsent(id) {
      val features = SearchEverywhereActionFeaturesProvider.getElementFeatures(element, startTime, state, localSummary, globalSummary)
      return@computeIfAbsent SearchEverywhereMLItemInfo(id, contributor.searchProviderId, features)
    }
  }

  @Synchronized
  fun getMLWeightIfDefined(element: GotoActionModel.MatchedValue): Double? {
    val id = getMLId(element)
    return cachedMLWeight[id]
  }

  @Synchronized
  fun getMLWeight(element: GotoActionModel.MatchedValue, contributor: SearchEverywhereContributor<*>, state: SearchEverywhereSearchState?): Double {
    val id = getMLId(element)
    return cachedMLWeight.computeIfAbsent(id) {
      predictor.predictMLWeight(element, contributor, this, state)
    }
  }

  private fun getMLId(element: GotoActionModel.MatchedValue): Int {
    val key = if (element.value is GotoActionModel.ActionWrapper) element.value.action else element.value
    var id = elementIds[key]
    if (id < 0) {
      id = idCounter.getAndIncrement()
      elementIds.put(key, id)
    }
    return id
  }

  private fun initContextInfo(project: Project?): SearchEverywhereMLContextInfo {
    val lastUsedToolwindow: String? = project?.let {
      val twm = ToolWindowManager.getInstance(project)
      var id: String? = null
      ApplicationManager.getApplication().invokeAndWait {
        id = twm.lastActiveToolWindowId
      }
      id
    }
    val features = SearchEverywhereContextFeaturesProvider.getContextFeatures(project, lastUsedToolwindow)
    return SearchEverywhereMLContextInfo(features)
  }

  @Synchronized
  fun clearCache() {
    cachedElementsInfo.clear()
    cachedMLWeight.clear()
  }
}

internal data class SearchEverywhereMLItemInfo(val id: Int, val contributorId: String, val features: Map<String, Any>)

internal data class SearchEverywhereMLContextInfo(val features: Map<String, Any>)