// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.productivityMetric.state

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "ProductivityMetricFeedbackInfoState", storages = [Storage("ProductivityMetricFeedbackInfoService.xml")])
class ProductivityMetricFeedbackInfoService : PersistentStateComponent<ProductivityMetricInfoState> {
  companion object {
    @JvmStatic
    fun getInstance(): ProductivityMetricFeedbackInfoService = service()
  }

  private var state = ProductivityMetricInfoState()

  override fun getState(): ProductivityMetricInfoState = state

  override fun loadState(state: ProductivityMetricInfoState) {
    this.state = state
  }
}

@Serializable
data class ProductivityMetricInfoState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false,
)