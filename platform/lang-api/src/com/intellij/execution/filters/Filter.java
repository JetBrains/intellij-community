/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.filters;

import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public interface Filter {

  Filter[] EMPTY_ARRAY = new Filter[0];

  class Result extends ResultItem {
    protected NextAction myNextAction = NextAction.EXIT;
    protected final List<ResultItem> myResultItems;

    public Result(final int highlightStartOffset, final int highlightEndOffset, @Nullable final HyperlinkInfo hyperlinkInfo) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo, null);
    }

    public Result(final int highlightStartOffset,
                  final int highlightEndOffset,
                  @Nullable final HyperlinkInfo hyperlinkInfo,
                  @Nullable final TextAttributes highlightAttributes) {
      super(highlightStartOffset, highlightEndOffset, hyperlinkInfo, highlightAttributes);
      myResultItems = null;
    }

    public Result(@NotNull List<ResultItem> resultItems) {
      super(-1, -1, null, null);
      myResultItems = resultItems;
    }

    public List<ResultItem> getResultItems() {
      List<ResultItem> resultItems = myResultItems;
      if (resultItems == null) {
        resultItems = Collections.singletonList((ResultItem)this);
      }
      return resultItems;
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
    @Nullable
    @Override
    public TextAttributes getHighlightAttributes() {
      return super.getHighlightAttributes();
    }

    /**
     * @deprecated This method will be removed. Result may be constructed using ResultItems, in that case this method will return incorrect value. Use {@link #getResultItems()} or {@link #getFirstHyperlinkInfo()} instead.
     */
    @Deprecated
    @Nullable
    @Override
    public HyperlinkInfo getHyperlinkInfo() {
      return super.getHyperlinkInfo();
    }

    @Nullable
    public HyperlinkInfo getFirstHyperlinkInfo() {
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
    /**
     * @deprecated use getter, the visibility of this field will be decreased.
     */
    @Deprecated
    public final int highlightStartOffset;
    /**
     * @deprecated use getter, the visibility of this field will be decreased.
     */
    @Deprecated
    public final int highlightEndOffset;
    /**
     * @deprecated use getter, the visibility of this field will be decreased.
     */
    @Deprecated @Nullable
    public final TextAttributes highlightAttributes;
    /**
     * @deprecated use getter, the visibility of this field will be decreased.
     */
    @Deprecated @Nullable
    public final HyperlinkInfo hyperlinkInfo;

    @SuppressWarnings("deprecation")
    public ResultItem(final int highlightStartOffset, final int highlightEndOffset, @Nullable final HyperlinkInfo hyperlinkInfo) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo, null);
    }

    @SuppressWarnings("deprecation")
    public ResultItem(final int highlightStartOffset,
                      final int highlightEndOffset,
                      @Nullable final HyperlinkInfo hyperlinkInfo,
                      @Nullable final TextAttributes highlightAttributes) {
      this.highlightStartOffset = highlightStartOffset;
      this.highlightEndOffset = highlightEndOffset;
      this.hyperlinkInfo = hyperlinkInfo;
      this.highlightAttributes = highlightAttributes;
    }

    public int getHighlightStartOffset() {
      //noinspection deprecation
      return highlightStartOffset;
    }

    public int getHighlightEndOffset() {
      //noinspection deprecation
      return highlightEndOffset;
    }

    @Nullable
    public TextAttributes getHighlightAttributes() {
      //noinspection deprecation
      return highlightAttributes;
    }

    @Nullable
    public HyperlinkInfo getHyperlinkInfo() {
      //noinspection deprecation
      return hyperlinkInfo;
    }
  }

  /**
   * Filters line by creating an instance of {@link Result}.
   *
   * @param line         The line to be filtered. Note that the line must contain a line
   *                     separator at the end.
   * @param entireLength The length of the entire text including the line passed for filtration.
   * @return <tt>null</tt>, if there was no match, otherwise, an instance of {@link Result}
   */
  @Nullable
  Result applyFilter(String line, int entireLength);
}
