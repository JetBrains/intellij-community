// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java15api;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * @author max
 */
public class Java15APIUsageInspection extends Java15APIUsageInspectionBase {
  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
    panel.add(new JLabel("Forbid API usages:"));

    JRadioButton projectRb = new JRadioButton("Respecting to project language level settings");
    panel.add(projectRb);
    JRadioButton customRb = new JRadioButton("Higher than:");
    panel.add(customRb);
    ButtonGroup gr = new ButtonGroup();
    gr.add(projectRb);
    gr.add(customRb);

    JComboBox<LanguageLevel> llCombo = new ComboBox<LanguageLevel>(LanguageLevel.values()) {
      @Override
      public void setEnabled(boolean b) {
        if (b == customRb.isSelected()) {
          super.setEnabled(b);
        }
      }
    };
    llCombo.setSelectedItem(myEffectiveLanguageLevel != null ? myEffectiveLanguageLevel : LanguageLevel.JDK_1_3);
    llCombo.setRenderer(new ListCellRendererWrapper<LanguageLevel>() {
      @Override
      public void customize(JList list, LanguageLevel value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(value.getPresentableText());
        }
      }
    });
    llCombo.addActionListener(e -> myEffectiveLanguageLevel = (LanguageLevel)llCombo.getSelectedItem());

    JPanel comboPanel = new JPanel(new BorderLayout());
    comboPanel.setBorder(JBUI.Borders.emptyLeft(20));
    comboPanel.add(llCombo, BorderLayout.WEST);
    panel.add(comboPanel);

    ActionListener actionListener = e -> {
      if (projectRb.isSelected()) {
        myEffectiveLanguageLevel = null;
      }
      else {
        myEffectiveLanguageLevel = (LanguageLevel)llCombo.getSelectedItem();
      }
      UIUtil.setEnabled(comboPanel, !projectRb.isSelected(), true);
    };
    projectRb.addActionListener(actionListener);
    customRb.addActionListener(actionListener);
    projectRb.setSelected(myEffectiveLanguageLevel == null);
    customRb.setSelected(myEffectiveLanguageLevel != null);
    UIUtil.setEnabled(comboPanel, !projectRb.isSelected(), true);
    return panel;
  }
}