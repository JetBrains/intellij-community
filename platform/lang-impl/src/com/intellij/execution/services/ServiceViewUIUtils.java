// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTabbedPaneUI;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.ContentUI;
import com.intellij.ui.content.TabbedPaneContentUI;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class ServiceViewUIUtils {
  private ServiceViewUIUtils() {
  }

  public static @NotNull ContentUI getServicesAlignedTabbedPaneContentUI() {
    TabbedPaneContentUI contentUI = new TabbedPaneContentUI(SwingConstants.TOP);
    JComponent component = contentUI.getComponent();
    if (component instanceof TabbedPaneWrapper.TabbedPaneHolder) {
      JComponent tabbedPane = ((TabbedPaneWrapper.TabbedPaneHolder)component).getTabbedPaneWrapper().getTabbedPane().getComponent();
      if (tabbedPane instanceof JTabbedPane) {
        ((JTabbedPane)tabbedPane).setUI(new ServiceViewDetailsTabbedPaneUI());
        tabbedPane.addPropertyChangeListener("UI", new PropertyChangeListener() {
          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            if (!(evt.getNewValue() instanceof ServiceViewDetailsTabbedPaneUI)) {
              ((JTabbedPane)tabbedPane).setUI(new ServiceViewDetailsTabbedPaneUI());
            }
          }
        });
      }
    }
    return contentUI;
  }

  public static @NotNull JPanel getServicesAlignedPanelWrapper(@NotNull JComponent wrapped) {
    return new NonOpaquePanel(wrapped) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = JBRunnerTabs.getTabLabelPreferredHeight();
        return size;
      }
    };
  }

  public static @NotNull JComponent wrapServicesAligned(@NotNull ActionToolbar toolbar) {
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(JBUI.Borders.empty(0, JBUI.scale(2)));
    Wrapper toolbarWrapper = new Wrapper() {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (size.height > 0) {
          size.height = JBRunnerTabs.getTabLabelPreferredHeight() - JBUI.scale(1); // without bottom border
        }
        return size;
      }
    };
    toolbarWrapper.setContent(toolbarComponent);
    return toolbarWrapper;
  }

  private static class ServiceViewDetailsTabbedPaneUI extends DarculaTabbedPaneUI {
    @Override
    protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
      Insets borderInsets = getContentBorderInsets(tabPlacement);
      return JBRunnerTabs.getTabLabelPreferredHeight() - borderInsets.top - borderInsets.bottom;
    }
  }
}
