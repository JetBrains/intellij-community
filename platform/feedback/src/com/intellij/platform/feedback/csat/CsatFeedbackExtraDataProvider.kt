// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Row
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CsatFeedbackExtraDataProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<CsatFeedbackExtraDataProvider> =
      ExtensionPointName.create<CsatFeedbackExtraDataProvider>("com.intellij.platform.feedback.csat.feedbackExtraDataProvider")
  }

  fun getFeedbackAgreementBlock(systemInfo: () -> Unit): (Row.(Project?) -> Unit)
  fun getExtraInfo(project: Project?): CsatFeedbackExtraInfo?
}

@ApiStatus.Internal
interface CsatFeedbackExtraInfo {
  fun serializeToJson(json: Json): JsonElement
}