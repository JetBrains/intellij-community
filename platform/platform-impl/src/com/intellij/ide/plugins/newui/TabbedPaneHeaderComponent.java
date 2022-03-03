// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * @author Alexander Lobas
 */
public class TabbedPaneHeaderComponent extends JPanel {
  private final JBValue myHeight = new JBValue.Float(30);
  private final JBValue myGap = new JBValue.Float(10);
  private final JBValue myYOffset = new JBValue.Float(32);

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
        int y = height > 0 ? height - myYOffset.get() : 0;
        int toolbarY = (y + height - toolbarSize.height) / 2;

        tabbedPane.setBounds(x, y, width, height - y);
        toolbar.setBounds(x + width + gap, toolbarY - JBUI.scale(1), toolbarSize.width, height);
      }
    });

    setOpaque(false);

    myTabbedPane.setOpaque(false);

    add(myTabbedPane);
    add(createToolbar(actions,
                      IdeBundle.message("plugin.manager.tooltip"),
                      AllIcons.General.GearPlain),
        BorderLayout.EAST);
  }

  static @NotNull JComponent createToolbar(@NotNull DefaultActionGroup actions,
                                           @Nullable @NlsActions.ActionText String tooltip,
                                           @NotNull Icon icon) {
    DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("PluginsHeaderToolbar", toolbarActionGroup, true);
    toolbar.setTargetComponent(toolbar.getComponent());
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarActionGroup.add(new DumbAwareAction(tooltip, tooltip, icon) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ListPopup actionGroupPopup = JBPopupFactory.getInstance().
          createActionGroupPopup(null, actions, e.getDataContext(), true, null, Integer.MAX_VALUE);

        HelpTooltip.setMasterPopup(e.getInputEvent().getComponent(), actionGroupPopup);
        Component component = toolbarComponent.getComponent(0);

        Container dialogComponent = ((JComponent)component).getRootPane().getParent();
        if (dialogComponent != null) {
          ComponentAdapter listener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
              movePopup();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
              movePopup();
            }

            private void movePopup() {
              if (actionGroupPopup.isVisible()) {
                actionGroupPopup.setLocation(new RelativePoint(component, getPopupPoint()).getScreenPoint());
                actionGroupPopup.pack(true, true);
              }
            }
          };
          dialogComponent.addComponentListener(listener);
          actionGroupPopup.addListener(new JBPopupListener() {
            @Override
            public void onClosed(@NotNull LightweightWindowEvent event) {
              dialogComponent.removeComponentListener(listener);
            }
          });
        }

        actionGroupPopup.show(new RelativePoint(component, getPopupPoint()));
      }

      private Point getPopupPoint() {
        int dH = UIUtil.isUnderWin10LookAndFeel() ? JBUIScale.scale(1) : 0;
        return new Point(JBUIScale.scale(2), toolbarComponent.getComponent(0).getHeight() - dH);
      }
    });
    toolbarComponent.setBorder(JBUI.Borders.empty());
    return toolbarComponent;
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

  public void addTab(@NotNull @Nls String title, @Nullable Icon icon) {
    myTabbedPane.addTab(title, icon, new JLabel());
    if (icon != null) {
      Component tab = myTabbedPane.getTabComponentAt(myTabbedPane.getTabCount() - 1);
      ((JLabel)tab).setHorizontalTextPosition(SwingConstants.LEFT);
    }
  }

  public void setTabTooltip(int index, @Nullable @Nls String tooltip) {
    myTabbedPane.setToolTipTextAt(index, tooltip);
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