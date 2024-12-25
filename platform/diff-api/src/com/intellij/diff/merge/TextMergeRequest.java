// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.contents.DocumentContent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class TextMergeRequest extends ThreesideMergeRequest {
  @Override
  public abstract @NotNull List<DocumentContent> getContents();

  @Override
  public abstract @NotNull DocumentContent getOutputContent();
}
