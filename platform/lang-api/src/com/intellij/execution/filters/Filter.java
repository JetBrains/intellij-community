// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface Filter {
  Filter[] EMPTY_ARRAY = new Filter[0];

  class Result extends ResultItem {
    private NextAction myNextAction = NextAction.EXIT;
    private final List<? extends ResultItem> myResultItems;

    public Result(final int highlightStartOffset, final int highlightEndOffset, final @Nullable HyperlinkInfo hyperlinkInfo) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo, null);
    }

    public Result(final int highlightStartOffset,
                  final int highlightEndOffset,
                  final @Nullable HyperlinkInfo hyperlinkInfo,
                  final @Nullable TextAttributes highlightAttributes) {
      super(highlightStartOffset, highlightEndOffset, hyperlinkInfo, highlightAttributes, null);
      myResultItems = null;
    }

    public Result(final int highlightStartOffset,
                  final int highlightEndOffset,
                  final @Nullable HyperlinkInfo hyperlinkInfo,
                  final @Nullable TextAttributes highlightAttributes,
                  final @Nullable TextAttributes followedHyperlinkAttributes) {
      super(highlightStartOffset, highlightEndOffset, hyperlinkInfo, highlightAttributes, followedHyperlinkAttributes);
      myResultItems = null;
    }

    public Result(final int highlightStartOffset,
                  final int highlightEndOffset,
                  final @Nullable HyperlinkInfo hyperlinkInfo,
                  final boolean grayedHyperlink) {
      super(highlightStartOffset, highlightEndOffset, hyperlinkInfo, grayedHyperlink);
      myResultItems = null;
    }

    public Result(@NotNull List<? extends ResultItem> resultItems) {
      super(0, 0, null, null, null);
      myResultItems = resultItems;
    }

    public @NotNull List<ResultItem> getResultItems() {
      List<? extends ResultItem> resultItems = myResultItems;
      if (resultItems == null) {
        resultItems = Collections.singletonList(this);
      }
      return Collections.unmodifiableList(resultItems);
    }

    /**
     * @deprecated This method will be removed. Result may be constructed using ResultItems, in that case this method will return incorrect value. Use {@link #getResultItems()} instead.
     */
    @Deprecated
    @Override
    public int getHighlightStartOffset() {
      return super.getHighlightStartOffset();
    }

    /**
     * @deprecated This method will be removed. Result may be constructed using ResultItems, in that case this method will return incorrect value. Use {@link #getResultItems()} instead.
     */
    @Deprecated
    @Override
    public int getHighlightEndOffset() {
      return super.getHighlightEndOffset();
    }

    /**
     * @deprecated This method will be removed. Result may be constructed using ResultItems, in that case this method will return incorrect value. Use {@link #getResultItems()} instead.
     */
    @Deprecated
    @Override
    public @Nullable TextAttributes getHighlightAttributes() {
      return super.getHighlightAttributes();
    }

    /**
     * @deprecated This method will be removed. Result may be constructed using ResultItems, in that case this method will return incorrect value. Use {@link #getResultItems()} or {@link #getFirstHyperlinkInfo()} instead.
     */
    @Deprecated
    @Override
    public @Nullable HyperlinkInfo getHyperlinkInfo() {
      return super.getHyperlinkInfo();
    }

    public @Nullable HyperlinkInfo getFirstHyperlinkInfo() {
      HyperlinkInfo info = super.getHyperlinkInfo();
      if (info == null && myResultItems != null) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < myResultItems.size(); i++) {
          ResultItem resultItem = myResultItems.get(i);
          if (resultItem.getHyperlinkInfo() != null) {
            return resultItem.getHyperlinkInfo();
          }
        }
      }
      return info;
    }

    public NextAction getNextAction() {
      return myNextAction;
    }

    public void setNextAction(NextAction nextAction) {
      myNextAction = nextAction;
    }
  }

  enum NextAction {
    EXIT, CONTINUE_FILTERING,
  }

  class ResultItem {
    private static final Map<TextAttributesKey, TextAttributes> GRAYED_BY_NORMAL_CACHE = new ConcurrentHashMap<>(2);

    static {
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        application.getMessageBus().connect().subscribe(EditorColorsManager.TOPIC, __ -> {
          // invalidate cache on Appearance Theme/Editor Scheme change
          GRAYED_BY_NORMAL_CACHE.clear();
        });
      }
    }

    private final int highlightStartOffset;
    private final int highlightEndOffset;
    /**
     * @deprecated use {@link #getHighlightAttributes()} instead, the visibility of this field will be decreased.
     */
    @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    public final @Nullable TextAttributes highlightAttributes;
    /**
     * @deprecated use {@link #getHyperlinkInfo()} instead, the visibility of this field will be decreased.
     */
    @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    public final @Nullable HyperlinkInfo hyperlinkInfo;

    private final TextAttributes myFollowedHyperlinkAttributes;

    public ResultItem(final int highlightStartOffset, final int highlightEndOffset, final @Nullable HyperlinkInfo hyperlinkInfo) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo, null, null);
    }

    public ResultItem(final int highlightStartOffset,
                      final int highlightEndOffset,
                      final @Nullable HyperlinkInfo hyperlinkInfo,
                      final @Nullable TextAttributes highlightAttributes) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo, highlightAttributes, null);
    }

    public ResultItem(int highlightStartOffset,
                      int highlightEndOffset,
                      @Nullable HyperlinkInfo hyperlinkInfo,
                      boolean grayedHyperlink) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo,
           grayedHyperlink ? getGrayedHyperlinkAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES) : null,
           grayedHyperlink ? getGrayedHyperlinkAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES) : null);
    }

    public ResultItem(final int highlightStartOffset,
                      final int highlightEndOffset,
                      final @Nullable HyperlinkInfo hyperlinkInfo,
                      final @Nullable TextAttributes highlightAttributes,
                      final @Nullable TextAttributes followedHyperlinkAttributes) {
      this.highlightStartOffset = highlightStartOffset;
      this.highlightEndOffset = highlightEndOffset;
      TextRange.assertProperRange(highlightStartOffset, highlightEndOffset, "");
      this.hyperlinkInfo = hyperlinkInfo;
      this.highlightAttributes = highlightAttributes;
      myFollowedHyperlinkAttributes = followedHyperlinkAttributes;
    }

    public int getHighlightStartOffset() {
      return highlightStartOffset;
    }

    public int getHighlightEndOffset() {
      return highlightEndOffset;
    }

    public @Nullable TextAttributes getHighlightAttributes() {
      return highlightAttributes;
    }

    public @Nullable TextAttributes getFollowedHyperlinkAttributes() {
      return myFollowedHyperlinkAttributes;
    }

    public @Nullable HyperlinkInfo getHyperlinkInfo() {
      return hyperlinkInfo;
    }

    /**
     * See {@link HighlighterLayer} for available predefined layers.
     */
    public int getHighlighterLayer() {
      return getHyperlinkInfo() != null ? HighlighterLayer.HYPERLINK : HighlighterLayer.CONSOLE_FILTER;
    }

    private static @Nullable TextAttributes getGrayedHyperlinkAttributes(@NotNull TextAttributesKey normalHyperlinkAttrsKey) {
      TextAttributes grayedHyperlinkAttrs = GRAYED_BY_NORMAL_CACHE.get(normalHyperlinkAttrsKey);
      if (grayedHyperlinkAttrs == null) {
        EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
        TextAttributes normalHyperlinkAttrs = globalScheme.getAttributes(normalHyperlinkAttrsKey);
        if (normalHyperlinkAttrs != null) {
          grayedHyperlinkAttrs = normalHyperlinkAttrs.clone();
          grayedHyperlinkAttrs.setForegroundColor(UIUtil.getInactiveTextColor());
          grayedHyperlinkAttrs.setEffectColor(UIUtil.getInactiveTextColor());
          GRAYED_BY_NORMAL_CACHE.put(normalHyperlinkAttrsKey, grayedHyperlinkAttrs);
        }
      }
      return grayedHyperlinkAttrs;
    }
  }

  /**
   * Filters line by creating an instance of {@link Result}.
   *
   * @param line         The line to be filtered. Note that the line must contain a line
   *                     separator at the end.
   * @param entireLength The length of the entire text including the line passed for filtration.
   * @return {@code null} if there was no match. Otherwise, an instance of {@link Result}
   */
  @Nullable
  Result applyFilter(@NotNull String line, int entireLength);
}
