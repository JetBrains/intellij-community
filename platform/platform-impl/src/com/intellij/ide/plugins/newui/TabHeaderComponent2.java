// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TabHeaderComponent2 extends JPanel {
  private final JBTabbedPane myTabbedPane = new JBTabbedPane();

  public TabHeaderComponent2(@NotNull DefaultActionGroup actions) {
    super(new AbstractLayoutManager() {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        assert parent.getComponentCount() == 2;

        int width = parent.getComponent(0).getPreferredSize().width + JBUI.scale(10) + parent.getComponent(1).getPreferredSize().width;
        return new Dimension(width, JBUI.scale(30));
      }

      @Override
      public void layoutContainer(Container parent) {
        assert parent.getComponentCount() == 2;

        int height = JBUI.scale(30);
        int offset = JBUI.scale(10);

        Component tabbedPane = parent.getComponent(0);
        Dimension tabbedSize = tabbedPane.getPreferredSize();

        Component toolbar = parent.getComponent(1);
        Dimension toolbarSize = toolbar.getPreferredSize();

        int x = (parent.getWidth() - (tabbedSize.width + offset + toolbarSize.width)) / 2;

        tabbedPane.setBounds(x, (height - tabbedSize.height) / 2, tabbedSize.width, height);
        x += tabbedSize.width + offset;

        toolbar.setBounds(x, (height - toolbarSize.height) / 2, toolbarSize.width, height);
      }
    });

    setBackground(JBUI.CurrentTheme.ToolWindow.headerBackground());
    setOpaque(true);

    add(myTabbedPane);
    add(TabHeaderComponent.createToolbar("Manage Repositories, Configure Proxy or Install Plugin from Disk", actions), BorderLayout.EAST);
  }

  public void setListener(@NotNull TabHeaderListener listener) {
    myTabbedPane.addChangeListener(e -> listener.selectionChanged(myTabbedPane.getSelectedIndex()));
  }

  public void addTab(@NotNull String title, @Nullable Icon icon) {
    myTabbedPane.addTab(title, icon, new JLabel());
  }

  public int getSelectionTab() {
    return myTabbedPane.getSelectedIndex();
  }

  public void setSelection(int index) {
    myTabbedPane.setSelectedIndex(index);
  }

  public void setSelectionWithEvents(int index) {
    setSelection(index);
  }
}