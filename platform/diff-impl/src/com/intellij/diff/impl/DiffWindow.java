// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffWindow extends DiffWindowBase {
  private final @NotNull DiffRequestChain myRequestChain;

  public DiffWindow(@Nullable Project project, @NotNull DiffRequestChain requestChain, @NotNull DiffDialogHints hints) {
    super(project, hints);
    myRequestChain = requestChain;
  }

  @Override
  protected @NotNull DiffRequestProcessor createProcessor() {
    return new MyCacheDiffRequestChainProcessor(myProject, myRequestChain);
  }

  private class MyCacheDiffRequestChainProcessor extends CacheDiffRequestChainProcessor {
    MyCacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain) {
      super(project, requestChain);
    }

    @Override
    protected @NotNull Runnable createAfterNavigateCallback() {
      return () -> DiffUtil.closeWindow(getWrapper().getWindow(), true, true);
    }

    @Override
    protected void setWindowTitle(@NotNull String title) {
      getWrapper().setTitle(title);
    }
  }
}
