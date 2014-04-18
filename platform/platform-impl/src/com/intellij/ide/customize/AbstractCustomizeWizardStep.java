/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.customize;

import com.intellij.ui.ClickListener;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;

public abstract class AbstractCustomizeWizardStep extends JPanel {
  protected static final int GAP = 20;

  abstract String getTitle();

  abstract String getHTMLHeader();

  abstract String getHTMLFooter();

  private static Color getSelectionBackground() {
    return ColorUtil.mix(UIUtil.getListSelectionBackground(), UIUtil.getLabelBackground(), .75);
  }

  protected static JPanel createBigButtonPanel(LayoutManager layout, final JToggleButton anchorButton, final Runnable action) {
    final JPanel panel = new JPanel(layout) {
      @Override
      public Color getBackground() {
        return anchorButton.isSelected() ? getSelectionBackground() : super.getBackground();
      }
    };
    panel.setOpaque(true);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        anchorButton.setSelected(true);
        action.run();
        return true;
      }
    }.installOn(panel);
    anchorButton.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        panel.repaint();
      }
    });
    return panel;
  }

  Component getDefaultFocusedComponent() {
    return null;
  }
}
