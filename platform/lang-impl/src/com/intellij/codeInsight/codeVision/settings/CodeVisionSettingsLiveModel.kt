// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.ViewableMap
import com.jetbrains.rd.util.reactive.ViewableSet

class CodeVisionSettingsLiveModel(lifetime: Lifetime) {

  val isRegistryEnabled: Property<Boolean> = Property(Registry.`is`("editor.codeVision.new")).also {
    val registry = Registry.get("editor.codeVision.new")
    registry.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        it.value = value.asBoolean()
      }
    }, lifetime.createNestedDisposable())
  }


  val isEnabled: Property<Boolean> = Property(CodeVisionSettings.instance().codeVisionEnabled)

  val isEnabledWithRegistry: Property<Boolean> = Property(isRegistryEnabled.value && isEnabled.value).also { property ->
    isRegistryEnabled.advise(lifetime) {
      property.value = it && isEnabled.value
    }

    isEnabled.advise(lifetime) {
      property.value = it && isRegistryEnabled.value
    }
  }

  val defaultPosition: Property<CodeVisionAnchorKind> = Property(CodeVisionSettings.instance().defaultPosition)
  val visibleMetricsAboveDeclarationCount: Property<Int> = Property(CodeVisionSettings.instance().visibleMetricsAboveDeclarationCount)
  val visibleMetricsNextToDeclarationCount: Property<Int> = Property(CodeVisionSettings.instance().visibleMetricsNextToDeclarationCount)
  val disabledCodeVisionProviderIds: ViewableSet<String> = ViewableSet(CodeVisionSettings.instance().state.disabledCodeVisionProviderIds.toMutableSet())
  val codeVisionGroupToPosition: ViewableMap<String, CodeVisionAnchorKind> = ViewableMap<String, CodeVisionAnchorKind>().apply {
    putAll(CodeVisionSettings.instance().state.codeVisionGroupToPosition.map { it.key to CodeVisionAnchorKind.valueOf(it.value) })
  }

  init {
    application.messageBus.connect(lifetime.createNestedDisposable())
      .subscribe(CodeVisionSettings.CODE_LENS_SETTINGS_CHANGED,
                 object : CodeVisionSettings.CodeVisionSettingsListener {
                   override fun globalEnabledChanged(newValue: Boolean) {
                     isEnabled.value = newValue
                   }

                   override fun defaultPositionChanged(newDefaultPosition: CodeVisionAnchorKind) {
                     defaultPosition.value = newDefaultPosition
                   }

                   override fun visibleMetricsAboveDeclarationCountChanged(newValue: Int) {
                     visibleMetricsAboveDeclarationCount.value = newValue
                   }

                   override fun visibleMetricsNextToDeclarationCountChanged(newValue: Int) {
                     visibleMetricsNextToDeclarationCount.value = newValue
                   }

                   override fun providerAvailabilityChanged(id: String, isEnabled: Boolean) {
                     if (isEnabled) {
                       disabledCodeVisionProviderIds.remove(id)
                     }
                     else {
                       disabledCodeVisionProviderIds.add(id)
                     }
                   }

                   override fun groupPositionChanged(id: String, position: CodeVisionAnchorKind) {
                     codeVisionGroupToPosition[id] = position
                   }
                 })
  }

  fun getAnchorLimit(anchor: CodeVisionAnchorKind): Int? {
    return when (anchor) {
      CodeVisionAnchorKind.Top -> visibleMetricsAboveDeclarationCount.value
      CodeVisionAnchorKind.Right -> visibleMetricsNextToDeclarationCount.value
      CodeVisionAnchorKind.Default -> getAnchorLimit(defaultPosition.value)

      else -> getAnchorLimit(defaultPosition.value)
    }
  }
}