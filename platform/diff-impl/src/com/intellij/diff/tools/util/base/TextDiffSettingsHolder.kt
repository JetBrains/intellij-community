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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

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
    var MERGE_AUTO_RESOLVE_IMPORT_CONFLICTS: Boolean = false,
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
    var ENABLE_SYNC_SCROLL: Boolean = true

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

    var isEnableSyncScroll: Boolean by placeDelegate(PlaceSettings::ENABLE_SYNC_SCROLL) { scrollingChanged() }

    var isEnableAligningChangesMode: Boolean by sharedDelegate(SharedSettings::ENABLE_ALIGNING_CHANGES_MODE) { alignModeChanged() }

    // Diff settings

    var highlightPolicy: HighlightPolicy = PLACE_SETTINGS.HIGHLIGHT_POLICY
      set(value) {
        if (field != value) {
          field = value
          if (value != HighlightPolicy.DO_NOT_HIGHLIGHT) { // do not persist confusing value as new default
            PLACE_SETTINGS.HIGHLIGHT_POLICY = value
          }
          PLACE_SETTINGS.eventDispatcher.multicaster.highlightPolicyChanged()
        }
      }

    var ignorePolicy: IgnorePolicy by placeDelegate(PlaceSettings::IGNORE_POLICY) { ignorePolicyChanged() }

    //
    // Merge
    //

    var isAutoApplyNonConflictedChanges: Boolean by sharedDelegate(SharedSettings::MERGE_AUTO_APPLY_NON_CONFLICTED_CHANGES)

    var isAutoResolveImportConflicts: Boolean by sharedDelegate(SharedSettings::MERGE_AUTO_RESOLVE_IMPORT_CONFLICTS) { resolveConflictsInImportsChanged() }

    var isEnableLstGutterMarkersInMerge: Boolean by sharedDelegate(SharedSettings::MERGE_LST_GUTTER_MARKERS)

    // Editor settings

    var isShowLineNumbers: Boolean by placeDelegate(PlaceSettings::SHOW_LINE_NUMBERS)

    var isShowWhitespaces: Boolean by placeDelegate(PlaceSettings::SHOW_WHITESPACES)

    var isShowIndentLines: Boolean by placeDelegate(PlaceSettings::SHOW_INDENT_LINES)

    var isUseSoftWraps: Boolean by placeDelegate(PlaceSettings::USE_SOFT_WRAPS)

    var highlightingLevel: HighlightingLevel by placeDelegate(PlaceSettings::HIGHLIGHTING_LEVEL)

    var contextRange: Int by sharedDelegate(SharedSettings::CONTEXT_RANGE) { foldingChanged() }

    var isExpandByDefault: Boolean by placeDelegate(PlaceSettings::EXPAND_BY_DEFAULT) { foldingChanged() }

    var isReadOnlyLock: Boolean by placeDelegate(PlaceSettings::READ_ONLY_LOCK)

    var breadcrumbsPlacement: BreadcrumbsPlacement by placeDelegate(PlaceSettings::BREADCRUMBS_PLACEMENT) { breadcrumbsPlacementChanged() }

    //
    // Impl
    //

    private fun <T> sharedDelegate(accessor: KMutableProperty1<SharedSettings, T>,
                                   onChange: Listener.() -> Unit = {}): ReadWriteProperty<Any?, T> {
      return object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
          return accessor.get(SHARED_SETTINGS)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
          if (value == accessor.get(SHARED_SETTINGS)) return
          accessor.set(SHARED_SETTINGS, value)
          onChange(SHARED_SETTINGS.eventDispatcher.multicaster)
        }
      }
    }

    private fun <T> placeDelegate(accessor: KMutableProperty1<PlaceSettings, T>,
                                  onChange: Listener.() -> Unit = {}): ReadWriteProperty<Any?, T> {
      return object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
          return accessor.get(PLACE_SETTINGS)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
          if (value == accessor.get(PLACE_SETTINGS)) return
          accessor.set(PLACE_SETTINGS, value)
          onChange(PLACE_SETTINGS.eventDispatcher.multicaster)
        }
      }
    }


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
      fun resolveConflictsInImportsChanged() {}
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
