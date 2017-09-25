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
package com.intellij.diff.impl;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class DiffWindowBase {
  @Nullable protected final Project myProject;
  @NotNull protected final DiffDialogHints myHints;

  private DiffRequestProcessor myProcessor;
  private WindowWrapper myWrapper;

  public DiffWindowBase(@Nullable Project project, @NotNull DiffDialogHints hints) {
    myProject = project;
    myHints = hints;
  }

  protected void init() {
    if (myWrapper != null) return;

    myProcessor = createProcessor();

    String dialogGroupKey = myProcessor.getContextUserData(DiffUserDataKeys.DIALOG_GROUP_KEY);
    if (dialogGroupKey == null) dialogGroupKey = "DiffContextDialog";

    myWrapper = new WindowWrapperBuilder(DiffUtil.getWindowMode(myHints), new MyPanel(myProcessor.getComponent()))
      .setProject(myProject)
      .setParent(myHints.getParent())
      .setDimensionServiceKey(dialogGroupKey)
      .setPreferredFocusedComponent(() -> myProcessor.getPreferredFocusedComponent())
      .setOnShowCallback(() -> myProcessor.updateRequest())
      .build();
    myWrapper.setImages(DiffUtil.DIFF_FRAME_ICONS);
    Disposer.register(myWrapper, myProcessor);
  }

  public void show() {
    init();
    myWrapper.show();
  }

  @NotNull
  protected abstract DiffRequestProcessor createProcessor();

  //
  // Delegate
  //

  protected void setWindowTitle(@NotNull String title) {
    myWrapper.setTitle(title);
  }

  protected void onAfterNavigate() {
    DiffUtil.closeWindow(myWrapper.getWindow(), true, true);
  }

  //
  // Getters
  //

  protected WindowWrapper getWrapper() {
    return myWrapper;
  }

  protected DiffRequestProcessor getProcessor() {
    return myProcessor;
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
