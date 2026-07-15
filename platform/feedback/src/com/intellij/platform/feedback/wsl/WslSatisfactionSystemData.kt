// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.wsl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Data attached automatically to the WSL satisfaction survey feedback, in addition to the answers:
 * IDE name, WSL distribution and version, and whether WSL commands run via the IJent/EEL API.
 */
@Serializable
internal data class WslSatisfactionSystemData(
  @NlsSafe val ideName: String,
  @NlsSafe val wslDistribution: String,
  val wslVersion: Int,
  val isEelApiUsed: Boolean,
  val commonData: CommonFeedbackSystemData,
) : SystemDataJsonSerializable {
  override fun serializeToJson(json: Json): JsonElement = json.encodeToJsonElement(this)
}
