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
package com.intellij.diff.impl

import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.util.xmlb.annotations.MapAnnotation
import java.util.*

@State(
  name = "DiffSettings",
  storages = arrayOf(Storage(value = DiffUtil.DIFF_CONFIG))
)
class DiffSettingsHolder : PersistentStateComponent<DiffSettingsHolder.State> {
  data class SharedSettings(
    var GO_TO_NEXT_FILE_ON_NEXT_DIFFERENCE: Boolean = true
  )

  data class PlaceSettings(
    var DIFF_TOOLS_ORDER: List<String> = ArrayList(),
    var SYNC_BINARY_EDITOR_SETTINGS: Boolean = true
  )

  class DiffSettings internal constructor(private val SHARED_SETTINGS: SharedSettings,
                                          private val PLACE_SETTINGS: PlaceSettings) {
    constructor() : this(SharedSettings(), PlaceSettings())

    var diffToolsOrder: List<String>
      get()      = PLACE_SETTINGS.DIFF_TOOLS_ORDER
      set(order) { PLACE_SETTINGS.DIFF_TOOLS_ORDER = order }

    var isGoToNextFileOnNextDifference: Boolean
      get()      = SHARED_SETTINGS.GO_TO_NEXT_FILE_ON_NEXT_DIFFERENCE
      set(value) { SHARED_SETTINGS.GO_TO_NEXT_FILE_ON_NEXT_DIFFERENCE = value }

    var isSyncBinaryEditorSettings: Boolean
      get()      = PLACE_SETTINGS.SYNC_BINARY_EDITOR_SETTINGS
      set(value) { PLACE_SETTINGS.SYNC_BINARY_EDITOR_SETTINGS = value }

    companion object {
      @JvmField val KEY: Key<DiffSettings> = Key.create("DiffSettings")

      @JvmStatic fun getSettings(): DiffSettings = getSettings(null)
      @JvmStatic fun getSettings(place: String?): DiffSettings = service<DiffSettingsHolder>().getSettings(place)
    }
  }

  fun getSettings(place: String?): DiffSettings {
    val placeKey = place ?: DiffPlaces.DEFAULT
    val placeSettings = myState.PLACES_MAP.getOrPut(placeKey, { defaultPlaceSettings(placeKey) })
    return DiffSettings(myState.SHARED_SETTINGS, placeSettings)
  }

  private fun copyStateWithoutDefaults(): State {
    val result = State()
    result.SHARED_SETTINGS = myState.SHARED_SETTINGS
    result.PLACES_MAP = DiffUtil.trimDefaultValues(myState.PLACES_MAP, { defaultPlaceSettings(it) })
    return result
  }

  private fun defaultPlaceSettings(place: String): PlaceSettings {
    return PlaceSettings()
  }


  class State {
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    var PLACES_MAP: TreeMap<String, PlaceSettings> = TreeMap()
    var SHARED_SETTINGS = SharedSettings()
  }

  private var myState: State = State()

  override fun getState(): State {
    return copyStateWithoutDefaults()
  }

  override fun loadState(state: State) {
    myState = state
  }
}