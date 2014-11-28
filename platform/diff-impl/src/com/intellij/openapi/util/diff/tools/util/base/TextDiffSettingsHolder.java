package com.intellij.openapi.util.diff.tools.util.base;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.comparison.ComparisonPolicy;
import com.intellij.openapi.util.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "TextDiffSettings",
  storages = {@Storage(
    file = DiffUtil.DIFF_CONFIG)})
public class TextDiffSettingsHolder implements PersistentStateComponent<TextDiffSettingsHolder.TextDiffSettings> {
  public static class TextDiffSettings {
    public static final Key<TextDiffSettings> KEY = Key.create("TextDiffSettings");

    // Presentation settings
    private boolean ENABLE_SYNC_SCROLL = true;

    // Diff settings
    private boolean INLINE_HIGHLIGHT = true;
    private ComparisonPolicy COMPARISON_POLICY = ComparisonPolicy.DEFAULT;

    // TODO: allow to change defaults
    // Editor settings
    public boolean SHOW_WHITESPACES = false;
    public boolean SHOW_LINE_NUMBERS = true;
    public boolean SHOW_INDENT_LINES = false;
    public boolean USE_SOFT_WRAPS = false;

    public TextDiffSettings() {
    }

    public TextDiffSettings(boolean ENABLE_SYNC_SCROLL,
                            boolean INLINE_HIGHLIGHT, @NotNull ComparisonPolicy COMPARISON_POLICY,
                            boolean SHOW_WHITESPACES, boolean SHOW_LINE_NUMBERS, boolean SHOW_INDENT_LINES, boolean USE_SOFT_WRAPS) {
      this.INLINE_HIGHLIGHT = INLINE_HIGHLIGHT;
      this.COMPARISON_POLICY = COMPARISON_POLICY;
      this.ENABLE_SYNC_SCROLL = ENABLE_SYNC_SCROLL;
      this.SHOW_WHITESPACES = SHOW_WHITESPACES;
      this.SHOW_LINE_NUMBERS = SHOW_LINE_NUMBERS;
      this.SHOW_INDENT_LINES = SHOW_INDENT_LINES;
      this.USE_SOFT_WRAPS = USE_SOFT_WRAPS;
    }

    @NotNull
    private TextDiffSettings copy() {
      return new TextDiffSettings(ENABLE_SYNC_SCROLL,
                                  INLINE_HIGHLIGHT, COMPARISON_POLICY,
                                  SHOW_WHITESPACES, SHOW_LINE_NUMBERS, SHOW_INDENT_LINES, USE_SOFT_WRAPS);
    }

    // Presentation settings

    public boolean isEnableSyncScroll() {
      return ENABLE_SYNC_SCROLL;
    }

    public void setEnableSyncScroll(boolean value) {
      this.ENABLE_SYNC_SCROLL = value;
    }

    // Diff settings

    public boolean isInlineHighlight() {
      return INLINE_HIGHLIGHT;
    }

    public void setInlineHighlight(boolean value) {
      INLINE_HIGHLIGHT = value;
    }

    @NotNull
    public ComparisonPolicy getComparisonPolicy() {
      return COMPARISON_POLICY;
    }

    public void setComparisonPolicy(@NotNull ComparisonPolicy policy) {
      COMPARISON_POLICY = policy;
    }

    // Editor settings

    public boolean isShowLineNumbers() {
      return this.SHOW_LINE_NUMBERS;
    }

    public void setShowLineNumbers(boolean state) {
      this.SHOW_LINE_NUMBERS = state;
    }

    public boolean isShowWhitespaces() {
      return this.SHOW_WHITESPACES;
    }

    public void setShowWhiteSpaces(boolean state) {
      this.SHOW_WHITESPACES = state;
    }

    public boolean isShowIndentLines() {
      return this.SHOW_INDENT_LINES;
    }

    public void setShowIndentLines(boolean state) {
      this.SHOW_INDENT_LINES = state;
    }

    public boolean isUseSoftWraps() {
      return this.USE_SOFT_WRAPS;
    }

    public void setUseSoftWraps(boolean state) {
      this.USE_SOFT_WRAPS = state;
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
