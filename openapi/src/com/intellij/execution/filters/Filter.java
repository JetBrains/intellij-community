/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.filters;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public interface Filter {
  class Result{
    public final int highlightStartOffset;
    public final int highlightEndOffset;
    public final HyperlinkInfo hyperlinkInfo;

    public Result(final int highlightStartOffset, final int highlightEndOffset, final HyperlinkInfo hyperlinkInfo) {
      this.highlightStartOffset = highlightStartOffset;
      this.highlightEndOffset = highlightEndOffset;
      this.hyperlinkInfo = hyperlinkInfo;
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
  Result applyFilter(String line, int entireLength);
}
