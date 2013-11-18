/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.java15api;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author max
 */
public class Java15APIUsageInspection extends Java15APIUsageInspectionBase {
  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
    panel.add(new JLabel("Forbid API usages:"));

    final JRadioButton projectRb = new JRadioButton("Respecting to project language level settings");
    panel.add(projectRb);
    final JRadioButton customRb = new JRadioButton("Higher than:");
    panel.add(customRb);
    final ButtonGroup gr = new ButtonGroup();
    gr.add(projectRb);
    gr.add(customRb);

    final DefaultComboBoxModel cModel = new DefaultComboBoxModel();
    for (LanguageLevel level : LanguageLevel.values()) {
      //noinspection unchecked
      cModel.addElement(level);
    }

    @SuppressWarnings("unchecked") final JComboBox llCombo = new JComboBox(cModel) {
      @Override
      public void setEnabled(boolean b) {
        if (b == customRb.isSelected()) {
          super.setEnabled(b);
        }
      }
    };
    llCombo.setSelectedItem(myEffectiveLanguageLevel != null ? myEffectiveLanguageLevel : LanguageLevel.JDK_1_3);
    //noinspection unchecked
    llCombo.setRenderer(new ListCellRendererWrapper<LanguageLevel>() {
      @Override
      public void customize(JList list, LanguageLevel value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(value.getPresentableText());
        }
      }
    });
    llCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myEffectiveLanguageLevel = (LanguageLevel)llCombo.getSelectedItem();
      }
    });
    final JPanel comboPanel = new JPanel(new BorderLayout());
    comboPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
    comboPanel.add(llCombo, BorderLayout.WEST);
    panel.add(comboPanel);

    final ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (projectRb.isSelected()) {
          myEffectiveLanguageLevel = null;
        }
        else {
          myEffectiveLanguageLevel = (LanguageLevel)llCombo.getSelectedItem();
        }
        UIUtil.setEnabled(comboPanel, !projectRb.isSelected(), true);
      }
    };
    projectRb.addActionListener(actionListener);
    customRb.addActionListener(actionListener);
    projectRb.setSelected(myEffectiveLanguageLevel == null);
    customRb.setSelected(myEffectiveLanguageLevel != null);
    UIUtil.setEnabled(comboPanel, !projectRb.isSelected(), true);
    return panel;
  }
}
