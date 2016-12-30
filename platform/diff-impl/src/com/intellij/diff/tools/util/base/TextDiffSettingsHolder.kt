/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.tools.util.base

import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.Key
import com.intellij.util.xmlb.annotations.MapAnnotation
import java.util.*

@State(
  name = "TextDiffSettings",
  storages = arrayOf(Storage(value = DiffUtil.DIFF_CONFIG))
)
class TextDiffSettingsHolder : PersistentStateComponent<TextDiffSettingsHolder.State> {
  companion object {
    @JvmField val KEY: Key<TextDiffSettings> = Key.create("TextDiffSettings")

    @JvmField val CONTEXT_RANGE_MODES: IntArray = intArrayOf(1, 2, 4, 8, -1)
    @JvmField val CONTEXT_RANGE_MODE_LABELS: Array<String> = arrayOf("1", "2", "4", "8", "Disable")

    @JvmStatic
    fun getInstance(): TextDiffSettingsHolder {
      return ServiceManager.getService(TextDiffSettingsHolder::class.java)
    }
  }

  internal data class SharedSettings(
    // Fragments settings
    var CONTEXT_RANGE: Int = 4,

    var MERGE_AUTO_APPLY_NON_CONFLICTED_CHANGES: Boolean = false,
    var MERGE_LST_GUTTER_MARKERS: Boolean = true
  )

  internal data class PlaceSettings(
    // Diff settings
    var HIGHLIGHT_POLICY: HighlightPolicy = HighlightPolicy.BY_WORD,
    var IGNORE_POLICY: IgnorePolicy = IgnorePolicy.DEFAULT,

    // Presentation settings
    var ENABLE_SYNC_SCROLL: Boolean = true,

    // Editor settings
    var SHOW_WHITESPACES: Boolean = false,
    var SHOW_LINE_NUMBERS: Boolean = true,
    var SHOW_INDENT_LINES: Boolean = false,
    var USE_SOFT_WRAPS: Boolean = false,
    var HIGHLIGHTING_LEVEL: HighlightingLevel = HighlightingLevel.INSPECTIONS,
    var READ_ONLY_LOCK: Boolean = true,

    // Fragments settings
    var EXPAND_BY_DEFAULT: Boolean = true
  )

  class TextDiffSettings internal constructor(val SHARED_SETTINGS: SharedSettings,
                                              val PLACE_SETTINGS: PlaceSettings) {
    constructor() : this(SharedSettings(), PlaceSettings())

    // Presentation settings

    var isEnableSyncScroll: Boolean
      get()      = PLACE_SETTINGS.ENABLE_SYNC_SCROLL
      set(value) { PLACE_SETTINGS.ENABLE_SYNC_SCROLL = value }

    // Diff settings

    var highlightPolicy: HighlightPolicy
      get()      = PLACE_SETTINGS.HIGHLIGHT_POLICY
      set(value) { PLACE_SETTINGS.HIGHLIGHT_POLICY = value }

    var ignorePolicy: IgnorePolicy
      get()      = PLACE_SETTINGS.IGNORE_POLICY
      set(value) { PLACE_SETTINGS.IGNORE_POLICY = value }

    //
    // Merge
    //

    var isAutoApplyNonConflictedChanges: Boolean
      get()      = SHARED_SETTINGS.MERGE_AUTO_APPLY_NON_CONFLICTED_CHANGES
      set(value) { SHARED_SETTINGS.MERGE_AUTO_APPLY_NON_CONFLICTED_CHANGES = value }

    var isEnableLstGutterMarkersInMerge: Boolean
      get()      = SHARED_SETTINGS.MERGE_LST_GUTTER_MARKERS
      set(value) { SHARED_SETTINGS.MERGE_LST_GUTTER_MARKERS = value }

    // Editor settings

    var isShowLineNumbers: Boolean
      get()      = PLACE_SETTINGS.SHOW_LINE_NUMBERS
      set(value) { PLACE_SETTINGS.SHOW_LINE_NUMBERS = value }

    var isShowWhitespaces: Boolean
      get()      = PLACE_SETTINGS.SHOW_WHITESPACES
      set(value) { PLACE_SETTINGS.SHOW_WHITESPACES = value }

    var isShowIndentLines: Boolean
      get()      = PLACE_SETTINGS.SHOW_INDENT_LINES
      set(value) { PLACE_SETTINGS.SHOW_INDENT_LINES = value }

    var isUseSoftWraps: Boolean
      get()      = PLACE_SETTINGS.USE_SOFT_WRAPS
      set(value) { PLACE_SETTINGS.USE_SOFT_WRAPS = value }

    var highlightingLevel: HighlightingLevel
      get()      = PLACE_SETTINGS.HIGHLIGHTING_LEVEL
      set(value) { PLACE_SETTINGS.HIGHLIGHTING_LEVEL = value }

    var contextRange: Int
      get()      = SHARED_SETTINGS.CONTEXT_RANGE
      set(value) { SHARED_SETTINGS.CONTEXT_RANGE = value }

    var isExpandByDefault: Boolean
      get()      = PLACE_SETTINGS.EXPAND_BY_DEFAULT
      set(value) { PLACE_SETTINGS.EXPAND_BY_DEFAULT = value }

    var isReadOnlyLock: Boolean
      get()      = PLACE_SETTINGS.READ_ONLY_LOCK
      set(value) { PLACE_SETTINGS.READ_ONLY_LOCK = value }

    //
    // Impl
    //

    companion object {
      @JvmStatic
      fun getSettings(): TextDiffSettings {
        return getSettings(null)
      }

      @JvmStatic
      fun getSettings(place: String?): TextDiffSettings {
        return getInstance().getSettings(place)
      }
    }
  }

  fun getSettings(place: String?): TextDiffSettings {
    val placeSettings = myState.PLACES_MAP.getOrPut(place ?: DiffPlaces.DEFAULT, { PlaceSettings() })
    return TextDiffSettings(myState.SHARED_SETTINGS, placeSettings)
  }

  class State {
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    internal var PLACES_MAP: TreeMap<String, PlaceSettings> = defaultPlaceSettings()
    internal var SHARED_SETTINGS = SharedSettings()

    companion object {
      private fun defaultPlaceSettings(): TreeMap<String, PlaceSettings> {
        val map = TreeMap<String, PlaceSettings>()

        val changes = PlaceSettings()
        changes.EXPAND_BY_DEFAULT = false
        val commit = PlaceSettings()
        commit.EXPAND_BY_DEFAULT = false

        map.put(DiffPlaces.DEFAULT, PlaceSettings())
        map.put(DiffPlaces.CHANGES_VIEW, changes)
        map.put(DiffPlaces.COMMIT_DIALOG, commit)

        return map
      }
    }
  }

  private var myState: State = State()

  override fun getState(): State {
    return myState
  }

  override fun loadState(state: State) {
    myState = state
  }
}