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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapper.Mode;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MergeWindow {
  @Nullable private final Project myProject;
  @NotNull private final MergeRequest myMergeRequest;

  private MergeRequestProcessor myProcessor;
  private WindowWrapper myWrapper;

  public MergeWindow(@Nullable Project project, @NotNull MergeRequest mergeRequest) {
    myProject = project;
    myMergeRequest = mergeRequest;
  }

  protected void init() {
    if (myWrapper != null) return;

    myProcessor = createProcessor();

    String dialogGroupKey = myProcessor.getContextUserData(DiffUserDataKeys.DIALOG_GROUP_KEY);
    if (dialogGroupKey == null) dialogGroupKey = "MergeDialog";

    myWrapper = new WindowWrapperBuilder(Mode.MODAL, new MyPanel(myProcessor.getComponent()))
      .setProject(myProject)
      .setPreferredFocusedComponent(myProcessor.getPreferredFocusedComponent())
      .setDimensionServiceKey(dialogGroupKey)
      .setOnShowCallback(new Runnable() {
        @Override
        public void run() {
          myProcessor.init();
          myProcessor.requestFocus(); // TODO: not needed for modal dialogs. Make a flag in WindowWrapperBuilder ?
        }
      })
      .setOnCloseHandler(new BooleanGetter() {
        @Override
        public boolean get() {
          return myProcessor.checkCloseAction();
        }
      })
      .build();
    myWrapper.setImage(ImageLoader.loadFromResource("/diff/Diff.png"));
    Disposer.register(myWrapper, myProcessor);

    new DumbAwareAction() {
      public void actionPerformed(final AnActionEvent e) {
        myWrapper.close();
      }
    }.registerCustomShortcutSet(CommonShortcuts.getCloseActiveWindow(), myProcessor.getComponent());
  }

  public void show() {
    init();
    myWrapper.show();
  }

  @NotNull
  private MergeRequestProcessor createProcessor() {
    return new MergeRequestProcessor(myProject, myMergeRequest) {
      @Override
      public void closeDialog() {
        myWrapper.close();
      }

      @Override
      protected void setWindowTitle(@NotNull String title) {
        myWrapper.setTitle(title);
      }
    };
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
