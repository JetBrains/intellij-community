// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereMLPredictor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class SearchEverywhereMLCache internal constructor(project: Project?) {
  private val startTime: Long = System.currentTimeMillis()
  private var idCounter = AtomicInteger(1)
  private val elementIds = IdentityHashMap<Any, Int>()

  // context features are calculated once per Search Everywhere session
  private val cachedContextInfo: SearchEverywhereMLFeatures by lazy { initContextInfo(project) }

  // element features & ML score are re-calculated on each typing because some of them might change (e.g. matching degree)
  private val cachedElementsInfo = mutableMapOf<Int, SearchEverywhereMLFeatures>()
  private val cachedMLWeight = mutableMapOf<Int, Double>()

  @Synchronized
  fun getContextFeatures(): Map<String, Any> {
    return cachedContextInfo.features
  }

  @Synchronized
  fun getElementFeatures(element: Any, contributor: SearchEverywhereContributor<*>): SearchEverywhereMLFeatures {
    val id = getMLId(element)
    return cachedElementsInfo.computeIfAbsent(id) {
      val features = SearchEverywhereFeaturesProvider.getElementFeatureProvider().getElementFeatures(element, contributor, startTime)
      SearchEverywhereMLFeatures(features.additionalData)
    }
  }

  @Synchronized
  fun getMLWeight(predictor: SearchEverywhereMLPredictor, element: Any, contributor: SearchEverywhereContributor<*>): Double {
    val id = getMLId(element)
    return cachedMLWeight.computeIfAbsent(id) {
      predictor.predictMLWeight(element, contributor, this)
    }
  }

  private fun getMLId(element: Any): Int {
    return elementIds.computeIfAbsent(element) { idCounter.getAndIncrement() }
  }

  private fun initContextInfo(project: Project?): SearchEverywhereMLFeatures {
    val lastUsedToolwindow: String? = project?.let {
      val twm = ToolWindowManager.getInstance(project)
      var id: String? = null
      ApplicationManager.getApplication().invokeAndWait {
        id = twm.lastActiveToolWindowId
      }
      id
    }

    //TODO: move query length to element features
    val features = SearchEverywhereFeaturesProvider.getContextFeaturesProvider().getContextFeatures(project, lastUsedToolwindow, -1)
    return SearchEverywhereMLFeatures(features)
  }

  @Synchronized
  fun clearCache() {
    cachedElementsInfo.clear()
    cachedMLWeight.clear()
  }
}

data class SearchEverywhereMLFeatures(val features: Map<String, Any>)