package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.diff.DiffRequestPanel;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.NoDiffRequest;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DiffRequestPanelImpl implements DiffRequestPanel {
  @NotNull private final JPanel myPanel;
  @NotNull private final MyCacheDiffRequestChainProcessor myProcessor;

  public DiffRequestPanelImpl(@Nullable Project project, @Nullable Window window) {
    myProcessor = new MyCacheDiffRequestChainProcessor(project, window);
    myProcessor.init();

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
    myProcessor.updateRequest();
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

  private static class MyCacheDiffRequestChainProcessor extends DiffRequestProcessor {
    @Nullable private final Window myWindow;
    @NotNull private final UserDataHolder myContext = new UserDataHolderBase();

    @NotNull private DiffRequest myRequest = NoDiffRequest.INSTANCE;

    public MyCacheDiffRequestChainProcessor(@Nullable Project project, @Nullable Window window) {
      super(project);
      myWindow = window;
    }

    public void setRequest(@Nullable DiffRequest request) {
      myRequest = request != null ? request : NoDiffRequest.INSTANCE;
    }

    @Override
    public void updateRequest(boolean force, @Nullable DiffUserDataKeys.ScrollToPolicy scrollToChangePolicy) {
      applyRequest(myRequest, force, scrollToChangePolicy);
    }

    @Override
    protected void setWindowTitle(@NotNull String title) {
      if (myWindow == null) return;
      if (myWindow instanceof JDialog) ((JDialog)myWindow).setTitle(title);
      if (myWindow instanceof JFrame) ((JFrame)myWindow).setTitle(title);
    }

    @Nullable
    @Override
    public <T> T getContextUserData(@NotNull Key<T> key) {
      return myContext.getUserData(key);
    }

    @Override
    public <T> void putContextUserData(@NotNull Key<T> key, @Nullable T value) {
      myContext.putUserData(key, value);
    }
  }
}
