// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.editor.smoothcaret

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class SmoothCaretUsageData(
  val isAnimatedCaret: Boolean,
  @param:NlsSafe val caretEasing: String,
  val smoothCaretBlinking: Boolean,
  val systemInfo: CommonFeedbackSystemData,
) : SystemDataJsonSerializable {
  
  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }

  override fun toString(): String = buildString {
    appendLine("Animated Caret Enabled: $isAnimatedCaret")
    appendLine("Caret Easing: $caretEasing")
    appendLine("Smooth Blinking: $smoothCaretBlinking")
    append(systemInfo.toString())
  }
}
