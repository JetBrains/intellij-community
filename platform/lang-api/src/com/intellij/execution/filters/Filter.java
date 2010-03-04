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
import org.jetbrains.annotations.Nullable;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public interface Filter {
  class Result{
    public final int highlightStartOffset;
    public final int highlightEndOffset;
    public final TextAttributes highlightAttributes;
    public final HyperlinkInfo hyperlinkInfo;
    @Nullable public final String foldingPlaceholder;

    public Result(final int highlightStartOffset, final int highlightEndOffset, final HyperlinkInfo hyperlinkInfo) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo, null);
    }

    public Result(final int highlightStartOffset, final int highlightEndOffset, final HyperlinkInfo hyperlinkInfo, final TextAttributes highlightAttributes) {
      this(highlightStartOffset, highlightEndOffset, hyperlinkInfo, highlightAttributes, null);
    }

    public Result(final int highlightStartOffset, final int highlightEndOffset, final HyperlinkInfo hyperlinkInfo, final TextAttributes highlightAttributes, @Nullable String foldingPlaceholder) {
      this.highlightStartOffset = highlightStartOffset;
      this.highlightEndOffset = highlightEndOffset;
      this.highlightAttributes = highlightAttributes;
      this.hyperlinkInfo = hyperlinkInfo;
      this.foldingPlaceholder = foldingPlaceholder;
    }
  }

  /**
   * Filters line by creating an instance of {@link Result}.
   *
   * @param line
   *     The line to be filtered. Note that the line must contain a line
   *     separator at the end.
   *
   * @param entireLength
   *     The length of the entire text including the line passed
   *     for filteration.
   *
   * @return
   *    <tt>null</tt>, if there was no match, otherwise, an instance of {@link Result}
   */
  @Nullable
  Result applyFilter(String line, int entireLength);
}
