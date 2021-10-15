// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.state.createdProject

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable


@Serializable
data class NewProjectInfoEntry(
  val projectTypeName: String,
  val creationDateTime: LocalDateTime,
  val isProcessed: Boolean = false,
  val isFeedbackShowed: Boolean = false
) {
  companion object {
    fun createNewProjectInfoEntry(projectTypeName: String): NewProjectInfoEntry {
      return NewProjectInfoEntry(projectTypeName, Clock.System.now().toLocalDateTime(TimeZone.UTC))
    }
  }
}
