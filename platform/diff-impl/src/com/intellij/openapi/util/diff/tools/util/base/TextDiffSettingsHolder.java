package com.intellij.openapi.util.diff.tools.util.base;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "TextDiffSettings",
  storages = {@Storage(
    file = DiffUtil.DIFF_CONFIG)})
public class TextDiffSettingsHolder implements PersistentStateComponent<TextDiffSettingsHolder.TextDiffSettings> {
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
      return getInstance().getState().copy();
    }

    @NotNull
    public static TextDiffSettings getSettingsDefaults() {
      return getInstance().getState();
    }
  }

  private TextDiffSettings myState = new TextDiffSettings();

  @NotNull
  public TextDiffSettings getState() {
    return myState;
  }

  public void loadState(TextDiffSettings state) {
    myState = state;
  }

  public static TextDiffSettingsHolder getInstance() {
    return ServiceManager.getService(TextDiffSettingsHolder.class);
  }
}
