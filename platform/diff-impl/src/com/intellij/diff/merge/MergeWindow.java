/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.merge;

import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

public class MergeWindow {
  @Nullable private final Project myProject;
  @NotNull private final MergeRequest myMergeRequest;

  private MyDialog myWrapper;

  public MergeWindow(@Nullable Project project, @NotNull MergeRequest mergeRequest) {
    myProject = project;
    myMergeRequest = mergeRequest;
  }

  protected void init() {
    MergeRequestProcessor processor = new MergeRequestProcessor(myProject, myMergeRequest) {
      @Override
      public void closeDialog() {
        myWrapper.doCancelAction();
      }

      @Override
      protected void setWindowTitle(@NotNull String title) {
        myWrapper.setTitle(title);
      }
    };

    myWrapper = new MyDialog(processor);
    myWrapper.init();
  }

  public void show() {
    init();
    myWrapper.show();
  }

  // TODO: use WindowWrapper
  private static class MyDialog extends DialogWrapper {
    @NotNull private final MergeRequestProcessor myProcessor;
    @NotNull private final MergeTool.BottomActions myBottomActions;

    public MyDialog(@NotNull MergeRequestProcessor processor) {
      super(processor.getProject(), true);
      myProcessor = processor;
      myBottomActions = myProcessor.getBottomActions();
    }

    @Override
    public void init() {
      super.init();
      Disposer.register(getDisposable(), myProcessor);
      getWindow().addWindowListener(new WindowAdapter() {
        @Override
        public void windowOpened(WindowEvent e) {
          myProcessor.init();
        }
      });
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return new MyPanel(myProcessor.getComponent());
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myProcessor.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    protected String getDimensionServiceKey() {
      return StringUtil.notNullize(myProcessor.getContextUserData(DiffUserDataKeys.DIALOG_GROUP_KEY), "MergeDialog");
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      List<Action> actions = ContainerUtil.skipNulls(ContainerUtil.list(myBottomActions.rightAction4, myBottomActions.rightAction3,
                                                                        myBottomActions.resolveAction, myBottomActions.cancelAction));
      if (myBottomActions.resolveAction != null) {
        myBottomActions.resolveAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
      }
      return actions.toArray(new Action[actions.size()]);
    }

    @NotNull
    @Override
    protected Action[] createLeftSideActions() {
      List<Action> actions = ContainerUtil.skipNulls(ContainerUtil.list(myBottomActions.leftAction1, myBottomActions.leftAction2,
                                                                        myBottomActions.leftAction3, myBottomActions.leftAction4));
      return actions.toArray(new Action[actions.size()]);
    }

    @NotNull
    @Override
    protected Action getOKAction() {
      if (myBottomActions.resolveAction != null) return myBottomActions.resolveAction;
      return super.getOKAction();
    }

    @NotNull
    @Override
    protected Action getCancelAction() {
      if (myBottomActions.cancelAction != null) return myBottomActions.cancelAction;
      return super.getCancelAction();
    }

    @Nullable
    @Override
    protected String getHelpId() {
      return myProcessor.getHelpId();
    }

    @Override
    public void doCancelAction() {
      if (!myProcessor.checkCloseAction()) return;
      super.doCancelAction();
    }
  }

  private static class MyPanel extends JPanel {
    public MyPanel(@NotNull JComponent content) {
      super(new BorderLayout());
      add(content, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension windowSize = DiffUtil.getDefaultDiffWindowSize();
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.max(windowSize.width, size.width), Math.max(windowSize.height, size.height));
    }
  }
}
