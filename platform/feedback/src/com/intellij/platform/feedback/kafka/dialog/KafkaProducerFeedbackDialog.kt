// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.kafka.dialog

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.kafka.bundle.KafkaFeedbackBundle

class KafkaProducerFeedbackDialog(project: Project?, forTest: Boolean) :
  KafkaConsumerProducerFeedbackDialog(project, forTest,
                                      KafkaFeedbackBundle.message("producer.dialog.description")) {

  override val myTitle: String = KafkaFeedbackBundle.message("dialog.top.title")
}