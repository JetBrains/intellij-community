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
    protected List<ResultItem> myResultItems;

    public Result(final int highlightStartOffset, final int highlightEndOffset, final HyperlinkInfo hyperlinkInfo) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo, null);
    }

    public Result(final int highlightStartOffset, final int highlightEndOffset, final HyperlinkInfo hyperlinkInfo, final TextAttributes highlightAttributes) {
      super(highlightStartOffset, highlightEndOffset, hyperlinkInfo, highlightAttributes);
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
    public final int highlightStartOffset;
    public final int highlightEndOffset;
    public final TextAttributes highlightAttributes;
    public final HyperlinkInfo hyperlinkInfo;

    public ResultItem(final int highlightStartOffset, final int highlightEndOffset, final HyperlinkInfo hyperlinkInfo) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo, null);
    }

    public ResultItem(final int highlightStartOffset, final int highlightEndOffset, final HyperlinkInfo hyperlinkInfo, final TextAttributes highlightAttributes) {
      this.highlightStartOffset = highlightStartOffset;
      this.highlightEndOffset = highlightEndOffset;
      this.hyperlinkInfo = hyperlinkInfo;
      this.highlightAttributes = highlightAttributes;
    }
  }

  /**
   * Filters line by creating an instance of {@link Result}.
   *
   *
   * @param line
   *     The line to be filtered. Note that the line must contain a line
   *     separator at the end.
   *
   * @param entireLength
   *     The length of the entire text including the line passed for filtration.
   *
   * @return
   *    <tt>null</tt>, if there was no match, otherwise, an instance of {@link Result}
   */
  @Nullable
  Result applyFilter(String line, int entireLength);
}
