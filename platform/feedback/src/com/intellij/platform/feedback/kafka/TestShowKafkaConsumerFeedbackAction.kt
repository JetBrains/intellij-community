// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.kafka

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.feedback.impl.IdleFeedbackTypes
import com.intellij.platform.feedback.kafka.bundle.KafkaFeedbackBundle

class TestShowKafkaConsumerFeedbackAction : AnAction(KafkaFeedbackBundle.message("kafka.consumer.test.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    IdleFeedbackTypes.KAFKA_CONSUMER_FEEDBACK.showNotification(e.project, true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}