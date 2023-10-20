// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.base

import com.intellij.diff.tools.util.breadcrumbs.BreadcrumbsPlacement
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.util.Key
import com.intellij.util.EventDispatcher
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.NonNls
import java.util.*

@State(name = "TextDiffSettings", storages = [(Storage(value = DiffUtil.DIFF_CONFIG))], category = SettingsCategory.CODE)
class TextDiffSettingsHolder : PersistentStateComponent<TextDiffSettingsHolder.State> {
  companion object {
    @JvmField val CONTEXT_RANGE_MODES: IntArray = intArrayOf(1, 2, 4, 8, -1)
    @JvmField val CONTEXT_RANGE_MODE_LABELS: Array<String> = arrayOf("1", "2", "4", "8", DiffBundle.message("configurable.diff.collapse.unchanged.ranges.disable"))
  }

  data class SharedSettings(
    // Fragments settings
    var CONTEXT_RANGE: Int = 4,

    var MERGE_AUTO_APPLY_NON_CONFLICTED_CHANGES: Boolean = false,
    var MERGE_LST_GUTTER_MARKERS: Boolean = true,
    var ENABLE_ALIGNING_CHANGES_MODE: Boolean = false
  ) {
    @Transient
    val eventDispatcher: EventDispatcher<TextDiffSettings.Listener> = EventDispatcher.create(TextDiffSettings.Listener::class.java)
  }

  data class PlaceSettings(
    // Diff settings
    var HIGHLIGHT_POLICY: HighlightPolicy = HighlightPolicy.BY_WORD,
    var IGNORE_POLICY: IgnorePolicy = IgnorePolicy.DEFAULT,

    // Editor settings
    var SHOW_WHITESPACES: Boolean = false,
    var SHOW_LINE_NUMBERS: Boolean = true,
    var SHOW_INDENT_LINES: Boolean = false,
    var USE_SOFT_WRAPS: Boolean = false,
    var HIGHLIGHTING_LEVEL: HighlightingLevel = HighlightingLevel.INSPECTIONS,
    var READ_ONLY_LOCK: Boolean = true,
    var BREADCRUMBS_PLACEMENT: BreadcrumbsPlacement = BreadcrumbsPlacement.HIDDEN,

    // Fragments settings
    var EXPAND_BY_DEFAULT: Boolean = true
  ) {
    @Transient
    val eventDispatcher: EventDispatcher<TextDiffSettings.Listener> = EventDispatcher.create(TextDiffSettings.Listener::class.java)
  }

  class TextDiffSettings internal constructor(private val SHARED_SETTINGS: SharedSettings,
                                              private val PLACE_SETTINGS: PlaceSettings,
                                              val place: String?) {
    constructor() : this(SharedSettings(), PlaceSettings(), null)

    fun addListener(listener: Listener, disposable: Disposable) {
      SHARED_SETTINGS.eventDispatcher.addListener(listener, disposable)
      PLACE_SETTINGS.eventDispatcher.addListener(listener, disposable)
    }

    // Presentation settings

    var isEnableSyncScroll: Boolean = true
      get()      = field
      set(value) { field = value
                   PLACE_SETTINGS.eventDispatcher.multicaster.scrollingChanged() }

    var isEnableAligningChangesMode: Boolean
      get() = SHARED_SETTINGS.ENABLE_ALIGNING_CHANGES_MODE
      set(value) { SHARED_SETTINGS.ENABLE_ALIGNING_CHANGES_MODE = value
                   SHARED_SETTINGS.eventDispatcher.multicaster.alignModeChanged() }

    // Diff settings

    var highlightPolicy: HighlightPolicy = PLACE_SETTINGS.HIGHLIGHT_POLICY
      set(value) {
        field = value
        if (value != HighlightPolicy.DO_NOT_HIGHLIGHT) { // do not persist confusing value as new default
          PLACE_SETTINGS.HIGHLIGHT_POLICY = value
        }
        PLACE_SETTINGS.eventDispatcher.multicaster.highlightPolicyChanged()
      }

    var ignorePolicy: IgnorePolicy
      get()      = PLACE_SETTINGS.IGNORE_POLICY
      set(value) { PLACE_SETTINGS.IGNORE_POLICY = value
                   PLACE_SETTINGS.eventDispatcher.multicaster.ignorePolicyChanged() }

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
      set(value) { SHARED_SETTINGS.CONTEXT_RANGE = value
                   PLACE_SETTINGS.eventDispatcher.multicaster.foldingChanged() }

    var isExpandByDefault: Boolean
      get()      = PLACE_SETTINGS.EXPAND_BY_DEFAULT
      set(value) { PLACE_SETTINGS.EXPAND_BY_DEFAULT = value
                   PLACE_SETTINGS.eventDispatcher.multicaster.foldingChanged() }

    var isReadOnlyLock: Boolean
      get()      = PLACE_SETTINGS.READ_ONLY_LOCK
      set(value) { PLACE_SETTINGS.READ_ONLY_LOCK = value }

    var breadcrumbsPlacement: BreadcrumbsPlacement
      get()      = PLACE_SETTINGS.BREADCRUMBS_PLACEMENT
      set(value) { PLACE_SETTINGS.BREADCRUMBS_PLACEMENT = value
                   PLACE_SETTINGS.eventDispatcher.multicaster.breadcrumbsPlacementChanged() }

    //
    // Impl
    //

    companion object {
      @JvmField val KEY: Key<TextDiffSettings> = Key.create("TextDiffSettings")

      @JvmStatic fun getSettings(): TextDiffSettings = getSettings(null)
      @JvmStatic fun getSettings(place: String?): TextDiffSettings = service<TextDiffSettingsHolder>().getSettings(place)
      internal fun getDefaultSettings(place: String): TextDiffSettings =
        TextDiffSettings(SharedSettings(), service<TextDiffSettingsHolder>().defaultPlaceSettings(place), place)
    }

    interface Listener : EventListener {
      fun highlightPolicyChanged() {}
      fun ignorePolicyChanged() {}
      fun breadcrumbsPlacementChanged() {}
      fun foldingChanged() {}
      fun scrollingChanged() {}
      fun alignModeChanged() {}

      abstract class Adapter : Listener
    }
  }

  fun getSettings(@NonNls place: String?): TextDiffSettings {
    val placeKey = place ?: DiffPlaces.DEFAULT
    val placeSettings = myState.PLACES_MAP.getOrPut(placeKey) { defaultPlaceSettings(placeKey) }
    return TextDiffSettings(myState.SHARED_SETTINGS, placeSettings, placeKey)
  }

  private fun copyStateWithoutDefaults(): State {
    val result = State()
    result.SHARED_SETTINGS = myState.SHARED_SETTINGS
    result.PLACES_MAP = DiffUtil.trimDefaultValues(myState.PLACES_MAP) { defaultPlaceSettings(it) }
    return result
  }

  private fun defaultPlaceSettings(place: String): PlaceSettings {
    val settings = PlaceSettings()
    if (place == DiffPlaces.CHANGES_VIEW) {
      settings.EXPAND_BY_DEFAULT = false
      settings.SHOW_LINE_NUMBERS = false
    }
    if (place == DiffPlaces.COMMIT_DIALOG) {
      settings.EXPAND_BY_DEFAULT = false
    }
    if (place == DiffPlaces.VCS_LOG_VIEW) {
      settings.EXPAND_BY_DEFAULT = false
    }
    if (place == DiffPlaces.VCS_FILE_HISTORY_VIEW) {
      settings.EXPAND_BY_DEFAULT = false
    }
    return settings
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
}
