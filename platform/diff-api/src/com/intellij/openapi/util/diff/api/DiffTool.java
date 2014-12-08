package com.intellij.openapi.util.diff.api;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

public interface DiffTool {
  ExtensionPointName<DiffTool> EP_NAME = ExtensionPointName.create("com.intellij.openapi.util.diff.api.DiffTool");

  @NotNull
  String getName();

  boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request);
}
