// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TabbedPaneHeaderComponent extends JPanel {
  private final JBValue myHeight = new JBValue.Float(30);
  private final JBValue myGap = new JBValue.Float(10);
  private final JBValue myYOffset = new JBValue.Float(3);

  private final JBTabbedPane myTabbedPane = new JBTabbedPane() {
    @Override
    public void setUI(TabbedPaneUI ui) {
      boolean value = UIManager.getBoolean("TabbedPane.contentOpaque");
      UIManager.getDefaults().put("TabbedPane.contentOpaque", Boolean.FALSE);
      try {
        super.setUI(ui);
      }
      finally {
        UIManager.getDefaults().put("TabbedPane.contentOpaque", Boolean.valueOf(value));
      }
    }
  };

  private final TabHeaderListener myListener;

  public TabbedPaneHeaderComponent(@NotNull DefaultActionGroup actions, @NotNull TabHeaderListener listener) {
    myListener = listener;

    setLayout(new AbstractLayoutManager() {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        assert parent.getComponentCount() == 2;

        int width = parent.getComponent(0).getPreferredSize().width * 2 + myGap.get() + parent.getComponent(1).getPreferredSize().width;
        return new Dimension(width, myHeight.get());
      }

      @Override
      public void layoutContainer(Container parent) {
        assert parent.getComponentCount() == 2;

        Component tabbedPane = parent.getComponent(0);
        Component toolbar = parent.getComponent(1);
        Dimension toolbarSize = toolbar.getPreferredSize();

        int width = tabbedPane.getPreferredSize().width * 2;
        int height = parent.getHeight();
        int gap = myGap.get();
        int x = (parent.getWidth() - width - gap - toolbarSize.width) / 2 - width / 4;
        int yOffset = myYOffset.get();

        tabbedPane.setBounds(x, yOffset, width, height - yOffset);
        toolbar.setBounds(x + width + gap, (height - toolbarSize.height) / 2, toolbarSize.width, height);
      }
    });

    setOpaque(false);

    myTabbedPane.setOpaque(false);

    add(myTabbedPane);
    add(TabHeaderComponent.createToolbar("Manage Repositories, Configure Proxy or Install Plugin from Disk", actions), BorderLayout.EAST);
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, 0, width, height + y);
  }

  public void setListener() {
    myTabbedPane.addChangeListener(e -> myListener.selectionChanged(myTabbedPane.getSelectedIndex()));
  }

  public void update() {
    doLayout();
    revalidate();
    myTabbedPane.doLayout();
    myTabbedPane.revalidate();
    repaint();
  }

  public void addTab(@NotNull String title, @Nullable Icon icon) {
    myTabbedPane.addTab(title, icon, new JLabel());
    if (icon != null) {
      Component tab = myTabbedPane.getTabComponentAt(myTabbedPane.getTabCount() - 1);
      ((JLabel)tab).setHorizontalTextPosition(SwingConstants.LEFT);
    }
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

  @Override
  public void addNotify() {
    super.addNotify();

    Runnable action = () -> setSelectionWithEvents(myTabbedPane.getSelectedIndex() == 0 ? 1 : 0);

    addTabSelectionAction(IdeActions.ACTION_NEXT_TAB, action);
    addTabSelectionAction(IdeActions.ACTION_PREVIOUS_TAB, action);
  }

  private void addTabSelectionAction(@NotNull String actionId, @NotNull Runnable callback) {
    EventHandler.addGlobalAction(this, actionId, () -> {
      if (myTabbedPane.getTabCount() > 0) {
        callback.run();
      }
    });
  }
}