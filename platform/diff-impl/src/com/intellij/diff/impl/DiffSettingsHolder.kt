// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.components.*
import com.intellij.openapi.util.Key
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.NonNls
import java.util.*

@State(name = DiffSettingsHolder.SETTINGS_KEY, storages = [(Storage(value = DiffUtil.DIFF_CONFIG))], category = SettingsCategory.CODE)
class DiffSettingsHolder : PersistentStateComponent<DiffSettingsHolder.State> {
  enum class IncludeInNavigationHistory {
    Always,
    OnlyIfOpen,
    Never;
  }

  data class SharedSettings(
    var GO_TO_NEXT_FILE_ON_NEXT_DIFFERENCE: Boolean = true,
    var IS_INCLUDED_IN_NAVIGATION_HISTORY: IncludeInNavigationHistory = IncludeInNavigationHistory.OnlyIfOpen,
  )

  data class PlaceSettings(
    var DIFF_TOOLS_ORDER: List<String> = ArrayList(),
    var SYNC_BINARY_EDITOR_SETTINGS: Boolean = true,
  )

  class DiffSettings internal constructor(
    private val SHARED_SETTINGS: SharedSettings,
    private val PLACE_SETTINGS: PlaceSettings,
  ) {
    constructor() : this(SharedSettings(), PlaceSettings())

    var diffToolsOrder: List<String>
      get() = PLACE_SETTINGS.DIFF_TOOLS_ORDER
      set(order) {
        PLACE_SETTINGS.DIFF_TOOLS_ORDER = order
      }

    var isGoToNextFileOnNextDifference: Boolean
      get() = SHARED_SETTINGS.GO_TO_NEXT_FILE_ON_NEXT_DIFFERENCE
      set(value) {
        SHARED_SETTINGS.GO_TO_NEXT_FILE_ON_NEXT_DIFFERENCE = value
      }

    // TODO: Trigger IdeDocumentHistoryImpl#removeInvalidFilesFrom on change somehow
    var isIncludedInNavigationHistory: IncludeInNavigationHistory
      get() = SHARED_SETTINGS.IS_INCLUDED_IN_NAVIGATION_HISTORY
      set(value) {
        SHARED_SETTINGS.IS_INCLUDED_IN_NAVIGATION_HISTORY = value
      }

    var isSyncBinaryEditorSettings: Boolean
      get() = PLACE_SETTINGS.SYNC_BINARY_EDITOR_SETTINGS
      set(value) {
        PLACE_SETTINGS.SYNC_BINARY_EDITOR_SETTINGS = value
      }

    companion object {
      @JvmField
      val KEY: Key<DiffSettings> = Key.create("DiffSettings")

      @JvmStatic
      fun getSettings(): DiffSettings = getSettings(null)
      @JvmStatic
      fun getSettings(place: String?): DiffSettings = service<DiffSettingsHolder>().getSettings(place)
      internal fun getDefaultSettings(place: String): DiffSettings =
        DiffSettings(SharedSettings(), service<DiffSettingsHolder>().defaultPlaceSettings(place))
    }
  }

  fun getSettings(@NonNls place: String?): DiffSettings {
    val placeKey = place ?: DiffPlaces.DEFAULT
    val placeSettings = myState.PLACES_MAP.getOrPut(placeKey) { defaultPlaceSettings(placeKey) }
    return DiffSettings(myState.SHARED_SETTINGS, placeSettings)
  }

  private fun copyStateWithoutDefaults(): State {
    val result = State()
    result.SHARED_SETTINGS = myState.SHARED_SETTINGS
    result.PLACES_MAP = DiffUtil.trimDefaultValues(myState.PLACES_MAP) { defaultPlaceSettings(it) }
    return result
  }

  private fun defaultPlaceSettings(place: String): PlaceSettings {
    val settings = PlaceSettings()
    if (place == DiffPlaces.VCS_LOG_VIEW) {
      settings.DIFF_TOOLS_ORDER = listOf(SimpleDiffTool::class.java.canonicalName, UnifiedDiffTool::class.java.canonicalName)
    }
    if (place == DiffPlaces.VCS_FILE_HISTORY_VIEW) {
      settings.DIFF_TOOLS_ORDER = listOf(UnifiedDiffTool::class.java.canonicalName)
    }
    if (place == DiffPlaces.CHANGES_VIEW) {
      settings.DIFF_TOOLS_ORDER = listOf(UnifiedDiffTool::class.java.canonicalName)
    }
    return settings
  }

  override fun noStateLoaded() {
    loadState(State())
  }

  class State {
    @OptionTag
    @XMap
    @JvmField var PLACES_MAP: TreeMap<String, PlaceSettings> = TreeMap()
    @JvmField var SHARED_SETTINGS: SharedSettings = SharedSettings()
  }

  private var myState: State = State()

  override fun getState(): State {
    return copyStateWithoutDefaults()
  }

  override fun loadState(state: State) {
    myState = state
  }

  internal companion object {
    internal const val SETTINGS_KEY = "DiffSettings"
  }
}