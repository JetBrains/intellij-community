// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFeaturesProvider.ContextInfo
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFeaturesProvider.ItemInfo
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereMLPredictor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

internal class SearchEverywhereMLCache internal constructor(project: Project?) {
  private val startTime: Long = System.currentTimeMillis()
  private var idCounter = AtomicInteger(1)
  private val elementIds = IdentityHashMap<Any, Int>()

  // context features are calculated once per Search Everywhere session
  private val cachedContextInfo: ContextInfo by lazy { initContextInfo(project) }

  // element features & ML score are re-calculated on each typing because some of them might change (e.g. matching degree)
  private val cachedElementsInfo = mutableMapOf<Int, ItemInfo>()
  private val cachedMLWeight = mutableMapOf<Int, Double>()

  private val predictor = SearchEverywhereMLPredictor()

  @Synchronized
  fun getContextFeatures(): ContextInfo {
    return cachedContextInfo
  }

  @Synchronized
  fun getElementFeatures(element: Any, contributor: SearchEverywhereContributor<*>, state: SearchEverywhereSearchState?): ItemInfo {
    val id = getMLId(element)
    return cachedElementsInfo.computeIfAbsent(id) {
      SearchEverywhereFeaturesProvider.getElementFeatureProvider().getElementFeatures(element, contributor, startTime, state)
    }
  }

  @Synchronized
  fun getMLWeightIfDefined(element: Any): Double? {
    val id = getMLId(element)
    return cachedMLWeight[id]
  }

  @Synchronized
  fun getMLWeight(element: Any, contributor: SearchEverywhereContributor<*>, state: SearchEverywhereSearchState?): Double {
    val id = getMLId(element)
    return cachedMLWeight.computeIfAbsent(id) {
      predictor.predictMLWeight(element, contributor, this, state)
    }
  }

  private fun getMLId(element: Any): Int {
    return elementIds.computeIfAbsent(element) { idCounter.getAndIncrement() }
  }

  private fun initContextInfo(project: Project?): ContextInfo {
    val lastUsedToolwindow: String? = project?.let {
      val twm = ToolWindowManager.getInstance(project)
      var id: String? = null
      ApplicationManager.getApplication().invokeAndWait {
        id = twm.lastActiveToolWindowId
      }
      id
    }
    return SearchEverywhereFeaturesProvider.getContextFeaturesProvider().getContextFeatures(project, lastUsedToolwindow)
  }

  @Synchronized
  fun clearCache() {
    cachedElementsInfo.clear()
    cachedMLWeight.clear()
  }
}