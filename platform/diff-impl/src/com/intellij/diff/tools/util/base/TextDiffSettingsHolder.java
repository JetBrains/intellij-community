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
package com.intellij.diff.tools.util.base;

import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Key;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

@State(
  name = "TextDiffSettings",
  storages = @Storage(value = DiffUtil.DIFF_CONFIG)
)
public class TextDiffSettingsHolder implements PersistentStateComponent<TextDiffSettingsHolder.State> {
  public static final Key<TextDiffSettings> KEY = Key.create("TextDiffSettings");

  public static final int[] CONTEXT_RANGE_MODES = {1, 2, 4, 8, -1};
  public static final String[] CONTEXT_RANGE_MODE_LABELS = {"1", "2", "4", "8", "Disable"};

  private final static class SharedSettings {
    // Fragments settings
    public int CONTEXT_RANGE = 4;
  }

  private static class PlaceSettings {
    // Diff settings
    public HighlightPolicy HIGHLIGHT_POLICY = HighlightPolicy.BY_WORD;
    public IgnorePolicy IGNORE_POLICY = IgnorePolicy.DEFAULT;

    // Presentation settings
    public boolean ENABLE_SYNC_SCROLL = true;

    // Editor settings
    public boolean SHOW_WHITESPACES = false;
    public boolean SHOW_LINE_NUMBERS = true;
    public boolean SHOW_INDENT_LINES = false;
    public boolean USE_SOFT_WRAPS = false;
    public HighlightingLevel HIGHLIGHTING_LEVEL = HighlightingLevel.INSPECTIONS;

    // Fragments settings
    public boolean EXPAND_BY_DEFAULT = true;
  }

  public static class TextDiffSettings {
    @NotNull public SharedSettings SHARED_SETTINGS = new SharedSettings();
    @NotNull public PlaceSettings PLACE_SETTINGS = new PlaceSettings();

    public TextDiffSettings() {
    }

    public TextDiffSettings(@NotNull SharedSettings SHARED_SETTINGS,
                            @NotNull PlaceSettings PLACE_SETTINGS) {
      this.SHARED_SETTINGS = SHARED_SETTINGS;
      this.PLACE_SETTINGS = PLACE_SETTINGS;
    }

    // Presentation settings

    public boolean isEnableSyncScroll() {
      return PLACE_SETTINGS.ENABLE_SYNC_SCROLL;
    }

    public void setEnableSyncScroll(boolean value) {
      this.PLACE_SETTINGS.ENABLE_SYNC_SCROLL = value;
    }

    // Diff settings

    @NotNull
    public HighlightPolicy getHighlightPolicy() {
      return PLACE_SETTINGS.HIGHLIGHT_POLICY;
    }

    public void setHighlightPolicy(@NotNull HighlightPolicy value) {
      PLACE_SETTINGS.HIGHLIGHT_POLICY = value;
    }

    @NotNull
    public IgnorePolicy getIgnorePolicy() {
      return PLACE_SETTINGS.IGNORE_POLICY;
    }

    public void setIgnorePolicy(@NotNull IgnorePolicy policy) {
      PLACE_SETTINGS.IGNORE_POLICY = policy;
    }

    // Editor settings

    public boolean isShowLineNumbers() {
      return PLACE_SETTINGS.SHOW_LINE_NUMBERS;
    }

    public void setShowLineNumbers(boolean state) {
      PLACE_SETTINGS.SHOW_LINE_NUMBERS = state;
    }

    public boolean isShowWhitespaces() {
      return PLACE_SETTINGS.SHOW_WHITESPACES;
    }

    public void setShowWhiteSpaces(boolean state) {
      PLACE_SETTINGS.SHOW_WHITESPACES = state;
    }

    public boolean isShowIndentLines() {
      return PLACE_SETTINGS.SHOW_INDENT_LINES;
    }

    public void setShowIndentLines(boolean state) {
      PLACE_SETTINGS.SHOW_INDENT_LINES = state;
    }

    public boolean isUseSoftWraps() {
      return PLACE_SETTINGS.USE_SOFT_WRAPS;
    }

    public void setUseSoftWraps(boolean state) {
      PLACE_SETTINGS.USE_SOFT_WRAPS = state;
    }

    @NotNull
    public HighlightingLevel getHighlightingLevel() {
      return PLACE_SETTINGS.HIGHLIGHTING_LEVEL;
    }

    public void setHighlightingLevel(@NotNull HighlightingLevel state) {
      PLACE_SETTINGS.HIGHLIGHTING_LEVEL = state;
    }

    public int getContextRange() {
      return SHARED_SETTINGS.CONTEXT_RANGE;
    }

    public void setContextRange(int value) {
      SHARED_SETTINGS.CONTEXT_RANGE = value;
    }

    public boolean isExpandByDefault() {
      return PLACE_SETTINGS.EXPAND_BY_DEFAULT;
    }

    public void setExpandByDefault(boolean value) {
      PLACE_SETTINGS.EXPAND_BY_DEFAULT = value;
    }

    //
    // Impl
    //

    @NotNull
    public static TextDiffSettings getSettings() {
      return getSettings(null);
    }

    @NotNull
    public static TextDiffSettings getSettings(@Nullable String place) {
      return getInstance().getSettings(place);
    }
  }

  @NotNull
  public TextDiffSettings getSettings(@Nullable String place) {
    if (place == null) place = DiffPlaces.DEFAULT;

    PlaceSettings placeSettings = myState.PLACES_MAP.get(place);
    if (placeSettings == null) {
      placeSettings = new PlaceSettings();
      myState.PLACES_MAP.put(place, placeSettings);
    }
    return new TextDiffSettings(myState.SHARED_SETTINGS, placeSettings);
  }

  public static class State {
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, PlaceSettings> PLACES_MAP = getDefaultPlaceSettings();
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

  public static TextDiffSettingsHolder getInstance() {
    return ServiceManager.getService(TextDiffSettingsHolder.class);
  }

  @NotNull
  public static Map<String, PlaceSettings> getDefaultPlaceSettings() {
    Map<String, PlaceSettings> map = new TreeMap<String, PlaceSettings>();

    PlaceSettings changes = new PlaceSettings();
    changes.EXPAND_BY_DEFAULT = false;
    PlaceSettings commit = new PlaceSettings();
    commit.EXPAND_BY_DEFAULT = false;

    map.put(DiffPlaces.DEFAULT, new PlaceSettings());
    map.put(DiffPlaces.CHANGES_VIEW, changes);
    map.put(DiffPlaces.COMMIT_DIALOG, commit);

    return map;
  }
}
