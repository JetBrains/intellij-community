// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.application.ApplicationManager

internal object SearchEverywhereFeaturesProvider {
  val localSummary: ActionsLocalSummary = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
  val globalSummary: ActionsGlobalSummaryManager = ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java)

  internal const val ADDITIONAL_DATA_KEY = "additionalData"
  internal const val CONTRIBUTOR_ID_KEY = "contributorId"
  internal const val ACTION_ID_KEY = "id"

  internal fun getElementFeatureProvider(): SearchEverywhereElementFeaturesProvider {
    return SearchEverywhereActionFeaturesProvider
  }

  internal fun getContextFeaturesProvider(): SearchEverywhereContextFeaturesProvider {
    return SearchEverywhereContextFeaturesProvider
  }

  data class ItemInfo(val id: String?, val contributorId: String, val additionalData: Map<String, Any>) {
    fun toMap(): Map<String, Any> {
      val result: HashMap<String, Any> = hashMapOf(
        CONTRIBUTOR_ID_KEY to contributorId
      )
      if (additionalData.isNotEmpty()) {
        result[ADDITIONAL_DATA_KEY] = additionalData
      }
      id?.let {
        result += ACTION_ID_KEY to it
      }
      return result
    }
  }
}