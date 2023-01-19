// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTabbedPaneUI;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.content.ContentUI;
import com.intellij.ui.content.TabbedPaneContentUI;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class ServiceViewUIUtils {
  private ServiceViewUIUtils() {
  }

  public static ContentUI getServicesAlignedTabbedPaneContentUI() {
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

  private static class ServiceViewDetailsTabbedPaneUI extends DarculaTabbedPaneUI {
    @Override
    protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
      Insets borderInsets = getContentBorderInsets(tabPlacement);
      return JBRunnerTabs.getTabLabelPreferredHeight() - borderInsets.top - borderInsets.bottom;
    }
  }
}
