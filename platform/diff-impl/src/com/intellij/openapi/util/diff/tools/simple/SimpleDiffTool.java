package com.intellij.openapi.util.diff.tools.simple;

import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

public class SimpleDiffTool implements FrameDiffTool {
  public static final SimpleDiffTool INSTANCE = new SimpleDiffTool();

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new SimpleDiffViewer(context, request);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return SimpleDiffViewer.canShowRequest(context, request);
  }

  @NotNull
  @Override
  public String getName() {
    return "Default Viewer";
  }
}
