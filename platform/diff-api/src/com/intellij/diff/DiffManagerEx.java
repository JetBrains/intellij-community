// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeRequestProducer;
import com.intellij.diff.merge.MergeTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DiffManagerEx extends DiffManager {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static @NotNull DiffManagerEx getInstance() {
    return (DiffManagerEx)DiffManager.getInstance();
  }

  //
  // Usage
  //

  @RequiresEdt
  public abstract void showDiffBuiltin(@Nullable Project project, @NotNull DiffRequest request);

  @RequiresEdt
  public abstract void showDiffBuiltin(@Nullable Project project, @NotNull DiffRequest request, @NotNull DiffDialogHints hints);

  @RequiresEdt
  public abstract void showDiffBuiltin(@Nullable Project project, @NotNull DiffRequestChain requests, @NotNull DiffDialogHints hints);

  @RequiresEdt
  public abstract void showMergeBuiltin(@Nullable Project project, @NotNull MergeRequest request);

  @RequiresEdt
  public abstract void showMergeBuiltin(@Nullable Project project, @NotNull MergeRequestProducer request, @NotNull DiffDialogHints hints);

  public abstract @NotNull List<DiffTool> getDiffTools();

  public abstract @NotNull List<MergeTool> getMergeTools();
}
