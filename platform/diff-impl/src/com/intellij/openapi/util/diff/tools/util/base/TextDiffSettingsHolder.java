/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff.tools.util.base;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@State(
  name = "TextDiffSettings",
  storages = {@Storage(
    file = DiffUtil.DIFF_CONFIG)})
public class TextDiffSettingsHolder implements PersistentStateComponent<TextDiffSettingsHolder.State> {
  public static class TextDiffSettings {
    public static final Key<TextDiffSettings> KEY = Key.create("TextDiffSettings");

    public static final int[] CONTEXT_RANGE_MODES = {1, 2, 4, 8, -1};
    public static final String[] CONTEXT_RANGE_MODE_LABELS = {"1", "2", "4", "8", "Disable"};

    private SharedSettings SHARED_SETTINGS = new SharedSettings();

    // Diff settings
    private HighlightPolicy HIGHLIGHT_POLICY = HighlightPolicy.BY_WORD;
    private IgnorePolicy IGNORE_POLICY = IgnorePolicy.DEFAULT;

    private static class SharedSettings {
      // Presentation settings
      private boolean ENABLE_SYNC_SCROLL = true;

      // Editor settings
      public boolean SHOW_WHITESPACES = false;
      public boolean SHOW_LINE_NUMBERS = true;
      public boolean SHOW_INDENT_LINES = false;
      public boolean USE_SOFT_WRAPS = false;

      // Fragments settings
      private int CONTEXT_RANGE = 4;
      private boolean EXPAND_BY_DEFAULT = true;
    }

    public TextDiffSettings() {
    }

    private TextDiffSettings(@NotNull SharedSettings SHARED_SETTINGS,
                             @NotNull HighlightPolicy HIGHLIGHT_POLICY, @NotNull IgnorePolicy IGNORE_POLICY) {
      this.SHARED_SETTINGS = SHARED_SETTINGS;
      this.HIGHLIGHT_POLICY = HIGHLIGHT_POLICY;
      this.IGNORE_POLICY = IGNORE_POLICY;
    }

    @NotNull
    private TextDiffSettings copy() {
      return new TextDiffSettings(SHARED_SETTINGS,
                                  HIGHLIGHT_POLICY, IGNORE_POLICY);
    }

    // Presentation settings

    public boolean isEnableSyncScroll() {
      return SHARED_SETTINGS.ENABLE_SYNC_SCROLL;
    }

    public void setEnableSyncScroll(boolean value) {
      this.SHARED_SETTINGS.ENABLE_SYNC_SCROLL = value;
    }

    // Diff settings

    @NotNull
    public HighlightPolicy getHighlightPolicy() {
      return HIGHLIGHT_POLICY;
    }

    public void setHighlightPolicy(@NotNull HighlightPolicy value) {
      HIGHLIGHT_POLICY = value;
    }

    @NotNull
    public IgnorePolicy getIgnorePolicy() {
      return IGNORE_POLICY;
    }

    public void setIgnorePolicy(@NotNull IgnorePolicy policy) {
      IGNORE_POLICY = policy;
    }

    // Editor settings

    public boolean isShowLineNumbers() {
      return this.SHARED_SETTINGS.SHOW_LINE_NUMBERS;
    }

    public void setShowLineNumbers(boolean state) {
      this.SHARED_SETTINGS.SHOW_LINE_NUMBERS = state;
    }

    public boolean isShowWhitespaces() {
      return this.SHARED_SETTINGS.SHOW_WHITESPACES;
    }

    public void setShowWhiteSpaces(boolean state) {
      this.SHARED_SETTINGS.SHOW_WHITESPACES = state;
    }

    public boolean isShowIndentLines() {
      return this.SHARED_SETTINGS.SHOW_INDENT_LINES;
    }

    public void setShowIndentLines(boolean state) {
      this.SHARED_SETTINGS.SHOW_INDENT_LINES = state;
    }

    public boolean isUseSoftWraps() {
      return this.SHARED_SETTINGS.USE_SOFT_WRAPS;
    }

    public void setUseSoftWraps(boolean state) {
      this.SHARED_SETTINGS.USE_SOFT_WRAPS = state;
    }

    public int getContextRange() {
      return this.SHARED_SETTINGS.CONTEXT_RANGE;
    }

    public void setContextRange(int value) {
      this.SHARED_SETTINGS.CONTEXT_RANGE = value;
    }

    public boolean isExpandByDefault() {
      return this.SHARED_SETTINGS.EXPAND_BY_DEFAULT;
    }

    public void setExpandByDefault(boolean value) {
      this.SHARED_SETTINGS.EXPAND_BY_DEFAULT = value;
    }

    //
    // Impl
    //

    @NotNull
    public static TextDiffSettings getSettings() {
      return getSettings(null);
    }

    @NotNull
    public static TextDiffSettings getSettingsDefaults() { // TODO: remove default settings?
      return getInstance().getSettings(null);
    }

    @NotNull
    public static TextDiffSettings getSettings(@Nullable String name) {
      if (name == null) return getInstance().getSettings(null).copy();
      return getInstance().getSettings(name);
    }
  }

  public static class State {
    public Map<String, TextDiffSettings> MAP = new HashMap<String, TextDiffSettings>();
  }

  private State myState = new State();

  @NotNull
  public TextDiffSettings getSettings(@Nullable String name) {
    if (name == null) name = "default";
    TextDiffSettings settings = myState.MAP.get(name);
    if (settings == null) {
      settings = new TextDiffSettings();
      myState.MAP.put(name, settings);
    }
    return settings;
  }

  @NotNull
  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  public static TextDiffSettingsHolder getInstance() {
    return ServiceManager.getService(TextDiffSettingsHolder.class);
  }
}
