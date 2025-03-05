// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl;

import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.NoDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DiffRequestPanelImpl implements DiffRequestPanel {
  private final @NotNull JPanel myPanel;
  private final @NotNull MyDiffRequestProcessor myProcessor;

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
    setRequest(request, null);
  }

  @Override
  public void setRequest(@Nullable DiffRequest request, @Nullable Object identity) {
    myProcessor.setRequest(request, identity);
  }

  @Override
  public <T> void putContextHints(@NotNull Key<T> key, @Nullable T value) {
    myProcessor.putContextUserData(key, value);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myProcessor.getPreferredFocusedComponent();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myProcessor);
  }

  private static class MyDiffRequestProcessor extends DiffRequestProcessor {
    private final @Nullable Window myWindow;

    private @NotNull DiffRequest myRequest = NoDiffRequest.INSTANCE;
    private @Nullable Object myRequestIdentity = null;

    MyDiffRequestProcessor(@Nullable Project project, @Nullable Window window) {
      super(project);
      myWindow = window;
    }

    public synchronized void setRequest(@Nullable DiffRequest request, @Nullable Object identity) {
      if (myRequestIdentity != null && identity != null && myRequestIdentity.equals(identity)) return;

      myRequest = request != null ? request : NoDiffRequest.INSTANCE;
      myRequestIdentity = identity;

      UIUtil.invokeLaterIfNeeded(() -> updateRequest());
    }

    @Override
    @RequiresEdt
    public synchronized void updateRequest(boolean force, @Nullable DiffUserDataKeysEx.ScrollToPolicy scrollToChangePolicy) {
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
