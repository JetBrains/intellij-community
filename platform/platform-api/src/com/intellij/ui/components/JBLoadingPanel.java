// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * @author Konstantin Bulenkov
 */
public class JBLoadingPanel extends JPanel {
  private final JPanel myPanel;
  private final LoadingDecorator myDecorator;
  private final Collection<JBLoadingPanelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public JBLoadingPanel(@Nullable LayoutManager manager, @NotNull Disposable parent) {
    this(manager, parent, -1);
  }

  public JBLoadingPanel(@Nullable LayoutManager manager, @NotNull Disposable parent, int startDelayMs) {
    this(manager, panel -> new LoadingDecorator(panel, parent, startDelayMs));
  }

  public JBLoadingPanel(@Nullable LayoutManager manager, @NotNull NotNullFunction<? super JPanel, ? extends LoadingDecorator> createLoadingDecorator) {
    super(new BorderLayout());
    myPanel = manager == null ? new JPanel() : new JPanel(manager);
    myPanel.setOpaque(false);
    myPanel.setFocusable(false);
    myDecorator = createLoadingDecorator.fun(myPanel);
    super.add(myDecorator.getComponent(), BorderLayout.CENTER);
  }

  @Override
  public void setLayout(LayoutManager mgr) {
    if (!(mgr instanceof BorderLayout)) {
      throw new IllegalArgumentException(String.valueOf(mgr));
    }
    super.setLayout(mgr);
    if (myDecorator != null) {
      super.add(myDecorator.getComponent(), BorderLayout.CENTER);
    }
  }

  public void setLoadingText(@Nls String text) {
    myDecorator.setLoadingText(text);
  }

  public void stopLoading() {
    myDecorator.stopLoading();
    for (JBLoadingPanelListener listener : myListeners) {
      listener.onLoadingFinish();
    }
  }

  public boolean isLoading() {
    return myDecorator.isLoading();
  }

  public void startLoading() {
    myDecorator.startLoading(false);
    for (JBLoadingPanelListener listener : myListeners) {
      listener.onLoadingStart();
    }
  }

  public void addListener(@NotNull JBLoadingPanelListener listener) {
    myListeners.add(listener);
  }

  public boolean removeListener(@NotNull JBLoadingPanelListener listener) {
    return myListeners.remove(listener);
  }

  public JPanel getContentPanel() {
    return myPanel;
  }

  @Override
  public Component add(Component comp) {
    return myPanel.add(comp);
  }

  @Override
  public Component add(Component comp, int index) {
    return myPanel.add(comp, index);
  }

  @Override
  public void add(Component comp, Object constraints) {
    myPanel.add(comp, constraints);
  }

  @Override
  public Dimension getPreferredSize() {
    return getContentPanel().getPreferredSize();
  }
}
