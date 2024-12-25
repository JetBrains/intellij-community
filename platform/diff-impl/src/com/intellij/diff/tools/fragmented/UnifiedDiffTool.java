// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffToolType;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer;
import com.intellij.openapi.diff.DiffBundle;
import org.jetbrains.annotations.NotNull;

public class UnifiedDiffTool implements FrameDiffTool {
  public static final UnifiedDiffTool INSTANCE = new UnifiedDiffTool();

  @Override
  public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (SimpleOnesideDiffViewer.canShowRequest(context, request)) return new SimpleOnesideDiffViewer(context, request);
    if (UnifiedDiffViewer.canShowRequest(context, request)) return new UnifiedDiffViewer(context, request);
    throw new IllegalArgumentException(request.toString());
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return SimpleOnesideDiffViewer.canShowRequest(context, request) || UnifiedDiffViewer.canShowRequest(context, request);
  }

  @Override
  public @NotNull String getName() {
    return DiffBundle.message("unified.viewer");
  }

  @Override
  public @NotNull DiffToolType getToolType() {
    return DiffToolType.Unified.INSTANCE;
  }
}
