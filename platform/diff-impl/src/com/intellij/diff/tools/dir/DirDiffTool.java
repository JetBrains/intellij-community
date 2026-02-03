// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.dir;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffTool;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.SuppressiveDiffTool;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.binary.BinaryDiffTool;
import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.diff.DiffBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class DirDiffTool implements FrameDiffTool, SuppressiveDiffTool {
  public static final DirDiffTool INSTANCE = new DirDiffTool();

  @Override
  public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return createViewer(context, (ContentDiffRequest)request);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return DirDiffViewer.canShowRequest(context, request);
  }

  @Override
  public @NotNull String getName() {
    return DiffBundle.message("directory.viewer");
  }

  public static @NotNull FrameDiffTool.DiffViewer createViewer(@NotNull DiffContext context,
                                                               @NotNull ContentDiffRequest request) {
    return new DirDiffViewer(context, request);
  }

  public static @NotNull FrameDiffTool.DiffViewer createViewer(@NotNull DiffContext context,
                                                               @NotNull DiffElement element1,
                                                               @NotNull DiffElement element2,
                                                               @NotNull DirDiffSettings settings,
                                                               @Nullable String helpID) {
    return new DirDiffViewer(context, element1, element2, settings, helpID);
  }

  @Override
  public List<Class<? extends DiffTool>> getSuppressedTools() {
    return Collections.singletonList(BinaryDiffTool.class);
  }
}
