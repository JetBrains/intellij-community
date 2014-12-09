package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffViewer;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

public interface DiffViewerWrapper {
  Key<DiffViewerWrapper> KEY = Key.create("Diff.DiffViewerWrapper");

  DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request, @NotNull DiffViewer wrappedViewer);
}
