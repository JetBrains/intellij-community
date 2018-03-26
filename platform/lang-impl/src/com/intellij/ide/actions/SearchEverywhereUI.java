// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class SearchEverywhereUI extends BorderLayoutPanel {
  private SETab mySelectedTab;

  public SearchEverywhereUI(@Nullable SearchEverywhereContributor selected) {

  }

  private class SETab extends JLabel {
    public SETab(String tabName) {
      super(tabName);
    }

    @Override
    public Border getBorder() {
      return JBUI.Borders.empty(0, 12);
    }

    @Override
    public Color getBackground() {
      return mySelectedTab == this ? new JBColor(0xdedede, 0x565a5e) : super.getBackground();
    }
  }
}
