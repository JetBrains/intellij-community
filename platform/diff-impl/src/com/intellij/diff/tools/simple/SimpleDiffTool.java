// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.diff.DiffBundle;
import org.jetbrains.annotations.NotNull;

public class SimpleDiffTool implements FrameDiffTool {
  public static final SimpleDiffTool INSTANCE = new SimpleDiffTool();

  @Override
  public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (SimpleOnesideDiffViewer.canShowRequest(context, request)) return new SimpleOnesideDiffViewer(context, request);
    if (SimpleDiffViewer.canShowRequest(context, request)) return new SimpleDiffViewer(context, request);
    if (SimpleThreesideDiffViewer.canShowRequest(context, request)) return new SimpleThreesideDiffViewer(context, request);
    throw new IllegalArgumentException(request.toString());
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return SimpleOnesideDiffViewer.canShowRequest(context, request) ||
           SimpleDiffViewer.canShowRequest(context, request) ||
           SimpleThreesideDiffViewer.canShowRequest(context, request);
  }

  @Override
  public @NotNull String getName() {
    return DiffBundle.message("side.by.side.viewer");
  }
}
