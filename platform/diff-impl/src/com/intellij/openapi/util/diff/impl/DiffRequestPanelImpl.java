package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.diff.DiffRequestPanel;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import com.intellij.openapi.util.diff.chains.SimpleDiffRequestChain;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class DiffRequestPanelImpl implements DiffRequestPanel {
  @NotNull private final JPanel myPanel;
  @NotNull private final MyCacheDiffRequestChainProcessor myProcessor;
  @NotNull private final MyRequestChain myChain;

  public DiffRequestPanelImpl(@Nullable Project project, @Nullable Window window) {
    myChain = new MyRequestChain();
    myProcessor = new MyCacheDiffRequestChainProcessor(project, window, myChain);

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
    myChain.setRequest(request);
    myProcessor.updateRequest();
  }

  @Override
  public <T> void putContextHints(@NotNull Key<T> key, @Nullable T value) {
    myChain.putUserData(key, value);
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

  private static class MyCacheDiffRequestChainProcessor extends CacheDiffRequestChainProcessor {
    @Nullable private final Window myWindow;

    public MyCacheDiffRequestChainProcessor(@Nullable Project project, @Nullable Window window, @NotNull DiffRequestChain requestChain) {
      super(project, requestChain);
      myWindow = window;
    }

    @Override
    protected void setWindowTitle(@NotNull String title) {
      if (myWindow == null) return;
      if (myWindow instanceof JDialog) ((JDialog)myWindow).setTitle(title);
      if (myWindow instanceof JFrame) ((JFrame)myWindow).setTitle(title);
    }
  }

  private static class MyRequestChain extends UserDataHolderBase implements DiffRequestChain {
    @Nullable private DiffRequest myRequest;

    public void setRequest(@Nullable DiffRequest request) {
      myRequest = request;
    }

    @Override
    public void setIndex(int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getIndex() {
      return 0;
    }

    @NotNull
    @Override
    public List<? extends DiffRequestPresentable> getRequests() {
      if (myRequest == null) return Collections.emptyList();
      return Collections.singletonList(new SimpleDiffRequestChain.DiffRequestPresentableWrapper(myRequest));
    }
  }
}
