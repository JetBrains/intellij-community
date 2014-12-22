package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.diff.DiffDialogHints;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffWindow {
  @Nullable private final Project myProject;

  @NotNull private final MyCacheDiffRequestChainProcessor myProcessor;
  @NotNull private final WindowWrapper myWrapper;
  @NotNull private final DiffDialogHints myHints;

  public DiffWindow(@Nullable Project project, @NotNull DiffRequestChain requestChain, @NotNull DiffDialogHints hints) {
    myProject = project;
    myHints = hints;

    String dialogGroupKey = requestChain.getUserData(DiffUserDataKeys.DIALOG_GROUP_KEY);
    if (dialogGroupKey == null) dialogGroupKey = "DiffContextDialog";

    myProcessor = new MyCacheDiffRequestChainProcessor(project, requestChain);
    myWrapper = new WindowWrapperBuilder(DiffUtil.getWindowMode(hints), myProcessor.getComponent())
      .setProject(project)
      .setParent(hints.getParent())
      .setDimensionServiceKey(dialogGroupKey)
      .setOnShowCallback(new Runnable() {
        @Override
        public void run() {
          myProcessor.updateRequest();
          myProcessor.requestFocus(); // TODO: not needed for modal dialogs. Make a flag in WindowWrapperBuilder ?
        }
      })
      .build();
    myWrapper.setImage(ImageLoader.loadFromResource("/diff/Diff.png"));
    Disposer.register(myWrapper, myProcessor);
  }

  public void show() {
    myWrapper.show();
  }

  private class MyCacheDiffRequestChainProcessor extends CacheDiffRequestChainProcessor {
    public MyCacheDiffRequestChainProcessor(@Nullable Project project,
                                            @NotNull DiffRequestChain requestChain) {
      super(project, requestChain, false);
    }

    @Override
    protected void setWindowTitle(@NotNull String title) {
      myWrapper.setTitle(title);
    }

    @Override
    protected void onAfterNavigate() {
      DiffUtil.closeWindow(myWrapper.getWindow(), true, true);
    }
  }
}
