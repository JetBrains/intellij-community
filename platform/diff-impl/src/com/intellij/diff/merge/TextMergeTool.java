// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class TextMergeTool implements MergeTool {
  public static final TextMergeTool INSTANCE = new TextMergeTool();

  private static final Logger LOG = Logger.getInstance(TextMergeTool.class);

  @Override
  public @NotNull MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return new TextMergeViewer(context, ((TextMergeRequest)request));
  }

  @Override
  public boolean canShow(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return request instanceof TextMergeRequest;
  }
}
