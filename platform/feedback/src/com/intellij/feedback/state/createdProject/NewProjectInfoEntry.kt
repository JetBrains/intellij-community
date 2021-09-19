// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.state.createdProject

import java.time.LocalDateTime

data class NewProjectInfoEntry(
  val projectTypeName: String,
  val creationDateTime: LocalDateTime,
  val isProcessed: Boolean = false,
  val isFeedbackShowed: Boolean = false
)
