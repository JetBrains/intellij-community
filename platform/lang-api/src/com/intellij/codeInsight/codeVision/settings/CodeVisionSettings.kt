// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import java.util.TreeSet

@State(name = "CodeVisionSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class CodeVisionSettings : PersistentStateComponent<CodeVisionSettings.State> {
  companion object {
    @JvmStatic
    fun getInstance(): CodeVisionSettings = ApplicationManager.getApplication().service<CodeVisionSettings>()

    @Deprecated(message = "Use CodeVisionSettings.getInstance()", ReplaceWith("getInstance()"))
    fun instance(): CodeVisionSettings = getInstance()

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
    var isEnabled: Boolean = true
    var defaultPosition: String? = null // determined by CodeVisionSettingsDefaults
    var visibleMetricsAboveDeclarationCount: Int = 5
    var visibleMetricsNextToDeclarationCount: Int = 5

    /**
     * Only providers that are not disabled by default are included in this set. Consumers should
     * refer to [CodeVisionSettings.isProviderEnabled] instead of this set directly.
     */
    var disabledCodeVisionProviderIds: TreeSet<String> = sortedSetOf()
    /**
     * Only providers that are not enabled by default are included in this set. Consumers should
     * refer to [CodeVisionSettings.isProviderEnabled] instead of this set directly.
     */
    var enabledCodeVisionProviderIds: TreeSet<String> = sortedSetOf()

    var codeVisionGroupToPosition: MutableMap<String, String> = mutableMapOf()
  }

  var codeVisionEnabled: Boolean
    get() = state.isEnabled
    set(value) {
      state.isEnabled = value
      listener.globalEnabledChanged(value)
    }

  var defaultPosition: CodeVisionAnchorKind
    get() = defaultPositionValue()
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

  /**
   * Ignores [State.isEnabled].
   */
  fun isProviderEnabled(id: String): Boolean {
    return when {
      state.disabledCodeVisionProviderIds.contains(id) -> false
      state.enabledCodeVisionProviderIds.contains(id) -> true
      else ->
        // Use the specified default if it exists, and assume enabled otherwise.
        CodeVisionSettingsDefaults.getInstance().defaultEnablementForProviderId[id] ?: true
    }
  }

  fun setProviderEnabled(id: String, isEnabled: Boolean) {
    val isEnabledByDefault = CodeVisionSettingsDefaults.getInstance().defaultEnablementForProviderId[id] ?: true
    if (isEnabled == isEnabledByDefault) {
      // The setting matches the default, so nothing needs to be stored.
      state.disabledCodeVisionProviderIds.remove(id)
      state.enabledCodeVisionProviderIds.remove(id)
    } else if (isEnabled) {
      state.disabledCodeVisionProviderIds.remove(id)
      state.enabledCodeVisionProviderIds.add(id)
    } else {
      state.disabledCodeVisionProviderIds.add(id)
      state.enabledCodeVisionProviderIds.remove(id)
    }

    listener.providerAvailabilityChanged(id, isEnabled)
  }

  val disabledCodeVisionProviderIds: Set<String>
    get() {
      return buildSet {
        // All explicitly specified providers that are disabled should be included.
        addAll(state.disabledCodeVisionProviderIds)

        // All providers that default to disabled should be added if they haven't been explicit enabled.
        addAll(
          CodeVisionSettingsDefaults.getInstance().defaultEnablementForProviderId
            .filterValues { enabled -> !enabled }
            .keys
            .filter { id -> id !in state.enabledCodeVisionProviderIds })
      }
    }

  // used externally
  @Suppress("MemberVisibilityCanBePrivate")
  fun getAnchorLimit(position: CodeVisionAnchorKind): Int {
    return when (position) {
      CodeVisionAnchorKind.Top -> visibleMetricsAboveDeclarationCount
      CodeVisionAnchorKind.Right -> visibleMetricsNextToDeclarationCount
      CodeVisionAnchorKind.Default -> getAnchorLimit(defaultPositionValue())
      else -> getAnchorLimit(defaultPositionValue())
    }
  }

  // used externally
  @Suppress("MemberVisibilityCanBePrivate")
  fun setAnchorLimit(defaultPosition: CodeVisionAnchorKind, i: Int) {
    when (defaultPosition) {
      CodeVisionAnchorKind.Top -> visibleMetricsAboveDeclarationCount = i
      CodeVisionAnchorKind.Right -> visibleMetricsNextToDeclarationCount = i
      CodeVisionAnchorKind.Default -> setAnchorLimit(defaultPositionValue(), i)
      else -> {}
    }
  }

  override fun getState(): State = synchronized(stateLock) {
    return state
  }

  override fun loadState(state: State): Unit = synchronized(stateLock) {
    this.state = state
  }

  private fun defaultPositionValue(): CodeVisionAnchorKind {
    return state.defaultPosition?.let { CodeVisionAnchorKind.valueOf(it) }
           ?: CodeVisionSettingsDefaults.getInstance().defaultPosition
  }

  fun resetDefaultPosition() {
    state.defaultPosition = null
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
