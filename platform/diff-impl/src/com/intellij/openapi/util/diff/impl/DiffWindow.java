package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.DiffDialogHints;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.util.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffWindow extends DiffWindowBase {
  @NotNull private final DiffRequestChain myRequestChain;

  public DiffWindow(@Nullable Project project, @NotNull DiffRequestChain requestChain, @NotNull DiffDialogHints hints) {
    super(project, hints);
    myRequestChain = requestChain;
  }

  @NotNull
  @Override
  protected DiffRequestProcessor createProcessor() {
    return new MyCacheDiffRequestChainProcessor(myProject, myRequestChain);
  }

  private class MyCacheDiffRequestChainProcessor extends CacheDiffRequestChainProcessor {
    public MyCacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain) {
      super(project, requestChain);
    }

    @Override
    protected void setWindowTitle(@NotNull String title) {
      getWrapper().setTitle(title);
    }

    @Override
    protected void onAfterNavigate() {
      DiffUtil.closeWindow(getWrapper().getWindow(), true, true);
    }
  }
}
