// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Row
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class CsatFeedbackExtraDataProvider {
  open fun getFeedbackAgreementBlock(systemInfo: () -> Unit): (Row.(Project?) -> Unit)? = null
  open fun getExtraInfo(project: Project?): CsatFeedbackExtraInfo? = null
}

@ApiStatus.Internal
interface CsatFeedbackExtraInfo {
  fun serializeToJson(json: Json): JsonElement
}