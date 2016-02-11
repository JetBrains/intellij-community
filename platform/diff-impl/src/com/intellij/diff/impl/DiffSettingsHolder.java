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
package com.intellij.diff.impl;

import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@State(
  name = "DiffSettings",
  storages = @Storage(DiffUtil.DIFF_CONFIG)
)
public class DiffSettingsHolder implements PersistentStateComponent<DiffSettingsHolder.State> {
  public static final Key<DiffSettings> KEY = Key.create("DiffSettings");

  private static class SharedSettings {
    public boolean GO_TO_NEXT_FILE_ON_NEXT_DIFFERENCE = true;
  }

  private static class PlaceSettings {
    @NotNull public List<String> DIFF_TOOLS_ORDER = new ArrayList<String>();
    public boolean SYNC_BINARY_EDITOR_SETTINGS = true;
  }

  public static class DiffSettings {
    @NotNull public SharedSettings SHARED_SETTINGS = new SharedSettings();
    @NotNull public PlaceSettings PLACE_SETTINGS = new PlaceSettings();

    public DiffSettings() {
    }

    public DiffSettings(@NotNull SharedSettings SHARED_SETTINGS,
                        @NotNull PlaceSettings PLACE_SETTINGS) {
      this.SHARED_SETTINGS = SHARED_SETTINGS;
      this.PLACE_SETTINGS = PLACE_SETTINGS;
    }

    @NotNull
    public List<String> getDiffToolsOrder() {
      return PLACE_SETTINGS.DIFF_TOOLS_ORDER;
    }

    public void setDiffToolsOrder(@NotNull List<String> order) {
      PLACE_SETTINGS.DIFF_TOOLS_ORDER = order;
    }

    public boolean isGoToNextFileOnNextDifference() {
      return SHARED_SETTINGS.GO_TO_NEXT_FILE_ON_NEXT_DIFFERENCE;
    }

    public void setGoToNextFileOnNextDifference(boolean value) {
      SHARED_SETTINGS.GO_TO_NEXT_FILE_ON_NEXT_DIFFERENCE = value;
    }

    public boolean isSyncBinaryEditorSettings() {
      return PLACE_SETTINGS.SYNC_BINARY_EDITOR_SETTINGS;
    }

    public void setSyncBinaryEditorSettings(boolean value) {
      PLACE_SETTINGS.SYNC_BINARY_EDITOR_SETTINGS= value;
    }

    //
    // Impl
    //

    @NotNull
    public static DiffSettings getSettings() {
      return getSettings(null);
    }

    @NotNull
    public static DiffSettings getSettings(@Nullable String place) {
      return getInstance().getSettings(place);
    }
  }

  @NotNull
  public DiffSettings getSettings(@Nullable String place) {
    if (place == null) place = DiffPlaces.DEFAULT;

    PlaceSettings placeSettings = myState.PLACES_MAP.get(place);
    if (placeSettings == null) {
      placeSettings = new PlaceSettings();
      myState.PLACES_MAP.put(place, placeSettings);
    }
    return new DiffSettings(myState.SHARED_SETTINGS, placeSettings);
  }

  public static class State {
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, PlaceSettings> PLACES_MAP = new HashMap<String, PlaceSettings>();
    public SharedSettings SHARED_SETTINGS = new SharedSettings();
  }

  private State myState = new State();

  @NotNull
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public static DiffSettingsHolder getInstance() {
    return ServiceManager.getService(DiffSettingsHolder.class);
  }
}
