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

import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.NoDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DiffRequestPanelImpl implements DiffRequestPanel {
  @NotNull private final JPanel myPanel;
  @NotNull private final MyDiffRequestProcessor myProcessor;

  public DiffRequestPanelImpl(@Nullable Project project, @Nullable Window window) {
    myProcessor = new MyDiffRequestProcessor(project, window);
    myProcessor.putContextUserData(DiffUserDataKeys.DO_NOT_CHANGE_WINDOW_TITLE, true);

    myPanel = new JPanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        super.addNotify();
        myProcessor.updateRequest();
      }
    };
    myPanel.add(myProcessor.getComponent());
  }

  @Override
  public void setRequest(@Nullable DiffRequest request) {
    myProcessor.setRequest(request);
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myProcessor.updateRequest();
      }
    });
  }

  @Override
  public <T> void putContextHints(@NotNull Key<T> key, @Nullable T value) {
    myProcessor.putContextUserData(key, value);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProcessor.getPreferredFocusedComponent();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myProcessor);
  }

  private static class MyDiffRequestProcessor extends DiffRequestProcessor {
    @Nullable private final Window myWindow;

    @NotNull private DiffRequest myRequest = NoDiffRequest.INSTANCE;

    public MyDiffRequestProcessor(@Nullable Project project, @Nullable Window window) {
      super(project);
      myWindow = window;
    }

    public void setRequest(@Nullable DiffRequest request) {
      myRequest = request != null ? request : NoDiffRequest.INSTANCE;
    }

    @Override
    public void updateRequest(boolean force, @Nullable DiffUserDataKeysEx.ScrollToPolicy scrollToChangePolicy) {
      applyRequest(myRequest, force, scrollToChangePolicy);
    }

    @Override
    protected void setWindowTitle(@NotNull String title) {
      if (myWindow == null) return;
      if (myWindow instanceof JDialog) ((JDialog)myWindow).setTitle(title);
      if (myWindow instanceof JFrame) ((JFrame)myWindow).setTitle(title);
    }
  }
}
