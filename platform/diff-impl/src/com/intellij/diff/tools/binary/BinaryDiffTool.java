// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.binary;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.diff.DiffBundle;
import org.jetbrains.annotations.NotNull;

public class BinaryDiffTool implements FrameDiffTool {
  public static final BinaryDiffTool INSTANCE = new BinaryDiffTool();

  @Override
  public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (OnesideBinaryDiffViewer.canShowRequest(context, request)) return new OnesideBinaryDiffViewer(context, request);
    if (TwosideBinaryDiffViewer.canShowRequest(context, request)) return new TwosideBinaryDiffViewer(context, request);
    if (ThreesideBinaryDiffViewer.canShowRequest(context, request)) return new ThreesideBinaryDiffViewer(context, request);
    throw new IllegalArgumentException(request.toString());
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return OnesideBinaryDiffViewer.canShowRequest(context, request) ||
           TwosideBinaryDiffViewer.canShowRequest(context, request) ||
           ThreesideBinaryDiffViewer.canShowRequest(context, request);
  }

  @Override
  public @NotNull String getName() {
    return DiffBundle.message("binary.file.viewer");
  }
}
