// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.contents.DiffContent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ThreesideMergeRequest extends MergeRequest {
  /**
   * 3 contents: left - middle - right (local - base - server)
   */
  public abstract @NotNull List<? extends DiffContent> getContents();

  public abstract @NotNull DiffContent getOutputContent();

  /**
   * @return contents names. Should have same length as {@link #getContents()}
   * Titles could be null.
   */
  public abstract @NotNull List<@Nls String> getContentTitles();
}
