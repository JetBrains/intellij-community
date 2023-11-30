// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.kafka.state

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "KafkaConsumerProducerInfoState", storages = [Storage("KafkaConsumerProducerFeedbackService.xml")])
class KafkaConsumerProducerFeedbackService : PersistentStateComponent<KafkaConsumerProducerInfoState> {
  companion object {
    @JvmStatic
    fun getInstance(): KafkaConsumerProducerFeedbackService = service()
  }

  private var state = KafkaConsumerProducerInfoState()

  override fun getState(): KafkaConsumerProducerInfoState = state

  override fun loadState(state: KafkaConsumerProducerInfoState) {
    this.state = state
  }
}

@Serializable
data class KafkaConsumerProducerInfoState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false,
  var consumerDialogIsOpened: Boolean = false,
  var producerDialogIsOpened: Boolean = false,
)