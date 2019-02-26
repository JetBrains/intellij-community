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

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MergeWindow {
  private static final Logger LOG = Logger.getInstance(MergeWindow.class);

  @Nullable private final Project myProject;
  @NotNull private final MergeRequest myMergeRequest;
  @NotNull private final DiffDialogHints myHints;

  private MergeRequestProcessor myProcessor;
  private WindowWrapper myWrapper;

  public MergeWindow(@Nullable Project project, @NotNull MergeRequest mergeRequest, @NotNull DiffDialogHints hints) {
    myProject = project;
    myMergeRequest = mergeRequest;
    myHints = hints;
  }

  protected void init() {
    if (myWrapper != null) return;

    myProcessor = new MergeRequestProcessor(myProject, myMergeRequest) {
      @Override
      public void closeDialog() {
        myWrapper.close();
      }

      @Override
      protected void setWindowTitle(@NotNull String title) {
        myWrapper.setTitle(title);
      }

      @Nullable
      @Override
      protected JRootPane getRootPane() {
        RootPaneContainer container = ObjectUtils.tryCast(myWrapper.getWindow(), RootPaneContainer.class);
        return container != null ? container.getRootPane() : null;
      }
    };

    myWrapper = new WindowWrapperBuilder(DiffUtil.getWindowMode(myHints), new MyPanel(myProcessor.getComponent()))
      .setProject(myProject)
      .setParent(myHints.getParent())
      .setDimensionServiceKey(StringUtil.notNullize(myProcessor.getContextUserData(DiffUserDataKeys.DIALOG_GROUP_KEY), "MergeDialog"))
      .setPreferredFocusedComponent(() -> myProcessor.getPreferredFocusedComponent())
      .setOnShowCallback(() -> myProcessor.init())
      .setOnCloseHandler(() -> myProcessor.checkCloseAction())
      .build();
    myWrapper.setImages(DiffUtil.DIFF_FRAME_ICONS);
    Disposer.register(myWrapper, myProcessor);

    Consumer<WindowWrapper> wrapperHandler = myHints.getWindowConsumer();
    if (wrapperHandler != null) wrapperHandler.consume(myWrapper);
  }

  public void show() {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      LOG.error("Merge dialog should not be shown under a write action, as it will disable any background activity.");
    }

    init();
    myWrapper.show();
  }

  private static class MyPanel extends JPanel {
    MyPanel(@NotNull JComponent content) {
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
