// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi

import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleTextAttributes
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ServiceCustomizationDto(
  val id: RunDashboardServiceId,
  val links: List<CustomLinkDto>,
  val text: List<TextSegmentWithAttributesDto>,
  val iconId: IconId?,
  val shouldClearTextAttributes: Boolean,
)

@ApiStatus.Internal
@Serializable
data class CustomLinkDto(
  val presentableText: @NlsSafe String,
  @kotlinx.serialization.Transient var callback: Runnable? = null,
)

@ApiStatus.Internal
@Serializable
data class TextSegmentWithAttributesDto(
  val value: @NlsSafe String,
  val attributes: SerializableTextAttributesType,
)

@ApiStatus.Internal
@Serializable
enum class SerializableTextAttributesType(@kotlinx.serialization.Transient val simpleTextAttributes: SimpleTextAttributes) {
  REGULAR_ATTRIBUTES(SimpleTextAttributes.REGULAR_ATTRIBUTES),
  GRAY_ATTRIBUTES(SimpleTextAttributes.GRAY_ATTRIBUTES), GRAYED_ATTRIBUTES(SimpleTextAttributes.GRAYED_ATTRIBUTES),
  REGULAR_BOLD_ATTRIBUTES(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES), GRAYED_BOLD_ATTRIBUTES(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);

  companion object {
    fun fromSimpleTextAttributes(simpleTextAttributes: SimpleTextAttributes): SerializableTextAttributesType {
      return entries.firstOrNull { it.simpleTextAttributes == simpleTextAttributes } ?: REGULAR_ATTRIBUTES
    }
  }
}