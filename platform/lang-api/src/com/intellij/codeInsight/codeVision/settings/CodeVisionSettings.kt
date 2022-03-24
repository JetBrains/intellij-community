// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import java.util.*

@State(name = "CodeVisionSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class CodeVisionSettings : PersistentStateComponent<CodeVisionSettings.State> {

  companion object {
    fun instance(): CodeVisionSettings = ApplicationManager.getApplication().getService(CodeVisionSettings::class.java)

    @Topic.AppLevel
    @JvmStatic
    val CODE_LENS_SETTINGS_CHANGED: Topic<CodeVisionSettingsListener> = Topic(CodeVisionSettingsListener::class.java,
                                                                              Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
  }

  private var state = State()
  private val stateLock = Any()

  private val listener: CodeVisionSettingsListener
    get() = ApplicationManager.getApplication().messageBus.syncPublisher(CODE_LENS_SETTINGS_CHANGED)

  class State {
    var isEnabled = true
    var defaultPosition = "Top"
    var visibleMetricsAboveDeclarationCount = 5
    var visibleMetricsNextToDeclarationCount = 5

    var disabledCodeVisionProviderIds: TreeSet<String> = sortedSetOf()
    var codeVisionGroupToPosition: MutableMap<String, String> = mutableMapOf()
  }

  var codeVisionEnabled: Boolean
    get() = state.isEnabled
    set(value) {
      state.isEnabled = value
      listener.globalEnabledChanged(value)
    }

  var defaultPosition: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.valueOf(state.defaultPosition)
    set(value) {
      state.defaultPosition = value.name
      listener.defaultPositionChanged(value)
    }

  var visibleMetricsAboveDeclarationCount: Int
    get() = state.visibleMetricsAboveDeclarationCount
    set(value) {
      state.visibleMetricsAboveDeclarationCount = value
      listener.visibleMetricsAboveDeclarationCountChanged(value)
    }

  var visibleMetricsNextToDeclarationCount: Int
    get() = state.visibleMetricsNextToDeclarationCount
    set(value) {
      state.visibleMetricsNextToDeclarationCount = value
      listener.visibleMetricsNextToDeclarationCountChanged(value)
    }

  fun getPositionForGroup(groupName: String): CodeVisionAnchorKind? {
    val position = state.codeVisionGroupToPosition[groupName] ?: return null
    return CodeVisionAnchorKind.valueOf(position)
  }

  fun setPositionForGroup(groupName: String, position: CodeVisionAnchorKind) {
    state.codeVisionGroupToPosition[groupName] = position.name
    listener.groupPositionChanged(groupName, position)
  }

  fun isProviderEnabled(id: String): Boolean {
    return state.disabledCodeVisionProviderIds.contains(id).not()
  }

  fun setProviderEnabled(id: String, isEnabled: Boolean) {
    if (isEnabled) {
      state.disabledCodeVisionProviderIds.remove(id)
    }
    else {
      state.disabledCodeVisionProviderIds.add(id)
    }
    listener.providerAvailabilityChanged(id, isEnabled)
  }

  fun getAnchorLimit(position: CodeVisionAnchorKind): Int {
    return when (position) {
      CodeVisionAnchorKind.Top -> visibleMetricsAboveDeclarationCount
      CodeVisionAnchorKind.Right -> visibleMetricsNextToDeclarationCount
      CodeVisionAnchorKind.Default -> getAnchorLimit(CodeVisionAnchorKind.valueOf(state.defaultPosition))
      else -> getAnchorLimit(CodeVisionAnchorKind.valueOf(state.defaultPosition))
    }
  }

  fun setAnchorLimit(defaultPosition: CodeVisionAnchorKind, i: Int) {
    when (defaultPosition) {
      CodeVisionAnchorKind.Top -> visibleMetricsAboveDeclarationCount = i
      CodeVisionAnchorKind.Right -> visibleMetricsNextToDeclarationCount = i
      CodeVisionAnchorKind.Default -> setAnchorLimit(CodeVisionAnchorKind.valueOf(state.defaultPosition), i)
    }
  }


  override fun getState(): State = synchronized(stateLock) {
    return state
  }

  override fun loadState(state: State) = synchronized(stateLock) {
    this.state = state
  }

  interface CodeVisionSettingsListener {
    fun globalEnabledChanged(newValue: Boolean) {}
    fun providerAvailabilityChanged(id: String, isEnabled: Boolean) {}
    fun groupPositionChanged(id: String, position: CodeVisionAnchorKind)
    fun defaultPositionChanged(newDefaultPosition: CodeVisionAnchorKind) {}
    fun visibleMetricsAboveDeclarationCountChanged(newValue: Int) {}
    fun visibleMetricsNextToDeclarationCountChanged(newValue: Int) {}
  }

}