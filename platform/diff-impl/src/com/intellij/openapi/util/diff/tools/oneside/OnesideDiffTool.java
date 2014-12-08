package com.intellij.openapi.util.diff.tools.oneside;

import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

public class OnesideDiffTool implements FrameDiffTool {
  public static final OnesideDiffTool INSTANCE = new OnesideDiffTool();

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new OnesideDiffViewer(context, request);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return OnesideDiffViewer.canShowRequest(context, request);
  }

  @NotNull
  @Override
  public String getName() {
    return "Oneside Viewer";
  }
}
