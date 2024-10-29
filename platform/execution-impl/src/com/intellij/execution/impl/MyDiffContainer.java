// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class MyDiffContainer extends JBLayeredPane implements Disposable {
  private final AnimatedIcon myIcon = new AsyncProcessIcon(getClass().getName());

  private final JComponent myContent;
  private final JComponent myLoadingPanel;
  private final JLabel myJLabel;

  MyDiffContainer(@NotNull JComponent content, @NotNull @Nls String text) {
    setLayout(new MyOverlayLayout());
    myContent = content;
    myLoadingPanel = new JPanel(new MyPanelLayout());
    myLoadingPanel.setOpaque(false);
    myLoadingPanel.add(myIcon);
    myJLabel = new JLabel(text);
    myJLabel.setForeground(NamedColorUtil.getInactiveTextColor());
    myLoadingPanel.add(myJLabel);

    add(myContent);
    add(myLoadingPanel, JLayeredPane.POPUP_LAYER);

    finishUpdating();
  }

  @Override
  public void dispose() {
    myIcon.dispose();
  }

  void startUpdating() {
    myLoadingPanel.setVisible(true);
    myIcon.resume();
  }

  void finishUpdating() {
    myIcon.suspend();
    myLoadingPanel.setVisible(false);
  }

  private final class MyOverlayLayout extends AbstractLayoutManager {
    @Override
    public void layoutContainer(Container parent) {
      // propagate bound to all children
      for(int i = 0; i< getComponentCount(); i++) {
        getComponent(i).setBounds(0, 0, getWidth(), getHeight());
      }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      return myContent.getPreferredSize();
    }
  }

  public JComponent getContent() {
    return myContent;
  }

  private final class MyPanelLayout extends AbstractLayoutManager {
    @Override
    public void layoutContainer(Container parent) {
      Dimension size = myIcon.getPreferredSize();
      Dimension preferredSize = myJLabel.getPreferredSize();
      int width = getWidth();
      int offset = width - size.width - 15 - preferredSize.width;
      myIcon.setBounds(offset, 0, size.width, size.height);
      myJLabel.setBounds(offset + size.width + 3, 0, preferredSize.width, size.height);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      return myContent.getPreferredSize();
    }
  }
}
