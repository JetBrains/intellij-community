// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ui.ClickListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;

public abstract class AbstractCustomizeWizardStep extends JPanel {
  protected static final int SMALL_GAP = 10;
  protected static final int GAP = 20;

  @Nls(capitalization = Nls.Capitalization.Title)
  public abstract String getTitle();

  /**
   * Content for title under top navigation.
   *
   * @return either a HTML string prefixed with <html>, or a text string not prefixed with <html>, which will be processed with StringUtil.escapeXmlEntities
   */
  @Nls
  public abstract String getHTMLHeader();

  /**
   * Content for footer above buttons.
   *
   * @return either a HTML string prefixed with <html>, or a text string not prefixed with <html>, which will be processed with StringUtil.escapeXmlEntities
   */
  @Nullable
  @Nls
  public String getHTMLFooter() {
    return null;
  }

  @NotNull
  protected static Color getSelectionBackground() {
    return ColorUtil.mix(UIUtil.getListSelectionBackground(true), UIUtil.getLabelBackground(), StartupUiUtil.isUnderDarcula() ? .5 : .75);
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

  public Component getDefaultFocusedComponent() {
    return null;
  }

  public void beforeShown(boolean forward) {
  }

  public boolean beforeOkAction() {
    return true;
  }
}
