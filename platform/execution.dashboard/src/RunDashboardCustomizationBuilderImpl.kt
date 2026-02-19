// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.execution.dashboard.RunDashboardCustomizationBuilder
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.platform.execution.dashboard.splitApi.CustomLinkDto
import com.intellij.platform.execution.dashboard.splitApi.SerializableTextAttributesType
import com.intellij.platform.execution.dashboard.splitApi.ServiceCustomizationDto
import com.intellij.platform.execution.dashboard.splitApi.TextSegmentWithAttributesDto
import com.intellij.ui.SimpleTextAttributes
import javax.swing.Icon

internal class RunDashboardCustomizationBuilderImpl : RunDashboardCustomizationBuilder {
  private val textFragments: MutableList<Pair<String, SimpleTextAttributes>> = mutableListOf()
  private val links: MutableMap<String, Runnable> = mutableMapOf()
  private var icon: Icon? = null
  private var clearText: Boolean = false

  override fun addText(text: String, attributes: SimpleTextAttributes): RunDashboardCustomizationBuilder {
    textFragments.add(text to attributes)
    return this
  }

  override fun setClearText(): RunDashboardCustomizationBuilder {
    clearText = true
    return this
  }

  override fun setIcon(icon: Icon): RunDashboardCustomizationBuilder {
    this.icon = icon
    return this
  }

  override fun addLink(value: String, callback: Runnable): RunDashboardCustomizationBuilder {
    links[value] = callback
    return this
  }

  internal fun buildDto(serviceId: RunDashboardServiceId): ServiceCustomizationDto {
    val textFragments = textFragments.map { (text, attributes) ->
      TextSegmentWithAttributesDto(value = text, attributes = SerializableTextAttributesType.fromSimpleTextAttributes(attributes))
    }

    return ServiceCustomizationDto(id = serviceId,
                                   links = links.map { (value, callback) -> CustomLinkDto(presentableText = value, callback = callback) },
                                   text = textFragments,
                                   iconId = icon?.rpcId(),
                                   shouldClearTextAttributes = clearText)
  }
}