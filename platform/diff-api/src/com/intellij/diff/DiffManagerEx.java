// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeRequestProducer;
import com.intellij.diff.merge.MergeTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DiffManagerEx extends DiffManager {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @NotNull
  public static DiffManagerEx getInstance() {
    return (DiffManagerEx)ApplicationManager.getApplication().getService(DiffManager.class);
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

  @NotNull
  public abstract List<DiffTool> getDiffTools();

  @NotNull
  public abstract List<MergeTool> getMergeTools();
}
