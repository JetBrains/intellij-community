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
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;

public abstract class AbstractCustomizeWizardStep extends JPanel {
  protected static final int SMALL_GAP = 10;
  protected static final int GAP = 20;

  protected abstract String getTitle();

  protected abstract String getHTMLHeader();

  protected abstract String getHTMLFooter();

  @NotNull
  protected static Color getSelectionBackground() {
    return ColorUtil.mix(UIUtil.getListSelectionBackground(), UIUtil.getLabelBackground(), UIUtil.isUnderDarcula() ? .5 : .75);
  }

  public static Border createSmallEmptyBorder() {
    return BorderFactory.createEmptyBorder(SMALL_GAP, SMALL_GAP, SMALL_GAP, SMALL_GAP);
  }

  public static BorderLayout createSmallBorderLayout() {
    return new BorderLayout(SMALL_GAP, SMALL_GAP);
  }

  public static void applyHeaderFooterStyle(@NotNull JBLabel label) {
    label.setForeground(UIUtil.getLabelDisabledForeground());
  }

  protected static JPanel createBigButtonPanel(LayoutManager layout, final JToggleButton anchorButton, final Runnable action) {
    final JPanel panel = new JPanel(layout) {
      @Override
      public Color getBackground() {
        return anchorButton.isSelected() ? getSelectionBackground() : super.getBackground();
      }
    };
    panel.setOpaque(anchorButton.isSelected());
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        anchorButton.setSelected(true);
        return true;
      }
    }.installOn(panel);
    anchorButton.addItemListener(new ItemListener() {
      boolean curState = anchorButton.isSelected();
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && curState != anchorButton.isSelected()) {
          action.run();
        }
        curState = anchorButton.isSelected();
        panel.setOpaque(curState);
        panel.repaint();
      }
    });
    return panel;
  }

  Component getDefaultFocusedComponent() {
    return null;
  }

  public void beforeShown(boolean forward) {
  }

  public boolean beforeOkAction() {
    return true;
  }
}
