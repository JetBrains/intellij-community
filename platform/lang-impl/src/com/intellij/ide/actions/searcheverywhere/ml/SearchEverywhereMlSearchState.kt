// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereActionFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereActionsRankingModel
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereActionsRankingModelProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.application.ApplicationManager

internal class SearchEverywhereMlSearchState(
  val sessionStartTime: Long, val searchStartTime: Long,
  val searchStartReason: SearchRestartReason, val tabId: String,
  val keysTyped: Int, val backspacesTyped: Int, val queryLength: Int
) {

  private val cachedElementsInfo = mutableMapOf<Int, SearchEverywhereMLItemInfo>()
  private val cachedMLWeight = mutableMapOf<Int, Double>()

  private val model: SearchEverywhereActionsRankingModel =
    SearchEverywhereActionsRankingModel(SearchEverywhereActionsRankingModelProvider())
  private val featuresProvider: SearchEverywhereElementFeaturesProvider = SearchEverywhereActionFeaturesProvider()

  private val localSummary: ActionsLocalSummary by lazy {
    ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
  }
  private val globalSummary: ActionsGlobalSummaryManager by lazy {
    ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java)
  }

  @Synchronized
  fun getElementFeatures(elementId: Int,
                         element: GotoActionModel.MatchedValue,
                         contributor: SearchEverywhereContributor<*>,
                         queryLength: Int): SearchEverywhereMLItemInfo {
    return cachedElementsInfo.computeIfAbsent(elementId) {
      val features = featuresProvider.getElementFeatures(element, sessionStartTime, queryLength, localSummary, globalSummary)
      return@computeIfAbsent SearchEverywhereMLItemInfo(elementId, contributor.searchProviderId, features)
    }
  }

  @Synchronized
  fun getMLWeightIfDefined(elementId: Int): Double? {
    return cachedMLWeight[elementId]
  }

  @Synchronized
  fun getMLWeight(elementId: Int,
                  element: GotoActionModel.MatchedValue,
                  contributor: SearchEverywhereContributor<*>,
                  context: SearchEverywhereMLContextInfo): Double {
    return cachedMLWeight.computeIfAbsent(elementId) {
      val features = mutableMapOf<String, Any>()
      features.putAll(context.features)
      features.putAll(getElementFeatures(elementId, element, contributor, queryLength).features)
      model.predict(features)
    }
  }
}

internal data class SearchEverywhereMLItemInfo(val id: Int, val contributorId: String, val features: Map<String, Any>)

internal data class SearchEverywhereMLContextInfo(val features: Map<String, Any>)