// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Deprecated
public class DiffPanelImpl implements DiffViewer {
  @NotNull private final MyDiffRequestProcessor myProcessor;

  public DiffPanelImpl(@Nullable Project project, @NotNull DiffRequest request, @NotNull Disposable disposable) {
    myProcessor = new MyDiffRequestProcessor(project, request);
    myProcessor.putContextUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL, SimpleDiffTool.INSTANCE);
    myProcessor.updateRequest(true);

    Disposer.register(disposable, myProcessor);
  }

  @Override
  public JComponent getComponent() {
    return myProcessor.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProcessor.getPreferredFocusedComponent();
  }

  @Nullable
  public Editor getEditor2() {
    DataContext dataContext = DataManager.getInstance().getDataContext(myProcessor.getPreferredFocusedComponent());
    FrameDiffTool.DiffViewer viewer = dataContext.getData(DiffDataKeys.DIFF_VIEWER);
    if (viewer instanceof TwosideTextDiffViewer) {
      return ((TwosideTextDiffViewer)viewer).getEditor2();
    }
    return null;
  }

  private static final class MyDiffRequestProcessor extends DiffRequestProcessor {
    @NotNull private final DiffRequest myRequest;

    private MyDiffRequestProcessor(@Nullable Project project, @NotNull DiffRequest request) {
      super(project);
      myRequest = request;
    }

    @Override
    @CalledInAwt
    public void updateRequest(boolean force, @Nullable DiffUserDataKeysEx.ScrollToPolicy scrollToChangePolicy) {
      applyRequest(myRequest, force, scrollToChangePolicy, true);
    }
  }
}
