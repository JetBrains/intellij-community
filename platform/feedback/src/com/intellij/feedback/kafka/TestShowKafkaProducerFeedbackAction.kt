// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.kafka

import com.intellij.feedback.common.IdleFeedbackTypes
import com.intellij.feedback.kafka.bundle.KafkaFeedbackBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TestShowKafkaProducerFeedbackAction : AnAction(KafkaFeedbackBundle.message("kafka.producer.test.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    IdleFeedbackTypes.KAFKA_PRODUCER_FEEDBACK.showNotification(e.project, true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}