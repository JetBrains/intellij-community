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

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.psi.PsiModifier;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedSymbolLocalInspection extends UnusedSymbolLocalInspectionBase {

  /**
   * use {@link com.intellij.codeInspection.deadCode.UnusedDeclarationInspection} instead
   */
  @Deprecated
  public UnusedSymbolLocalInspection() {
  }

  public class OptionsPanel {
    private JCheckBox myCheckLocalVariablesCheckBox;
    private JComboBox<String> myCheckClassesCheckBox;
    private JComboBox<String> myCheckFieldsCheckBox;
    private JComboBox<String> myCheckMethodsCheckBox;
    private JComboBox<String> myCheckParametersCheckBox;
    private JPanel myPanel;
    private JCheckBox myCheckGettersSettersCheckBox;

    public OptionsPanel() {
      myCheckLocalVariablesCheckBox.setSelected(LOCAL_VARIABLE);
      myCheckGettersSettersCheckBox.setSelected(!isIgnoreAccessors());
      String[] visibilities = new String[] {"none", PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE};
      myCheckClassesCheckBox.setModel(new DefaultComboBoxModel<>(visibilities));
      myCheckFieldsCheckBox.setModel(new DefaultComboBoxModel<>(visibilities));
      myCheckMethodsCheckBox.setModel(new DefaultComboBoxModel<>(visibilities));
      myCheckParametersCheckBox.setModel(new DefaultComboBoxModel<>(visibilities));

      final ListCellRendererWrapper<String> renderer = new ListCellRendererWrapper<String>() {
        @Override
        public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
          if (value != null && !"none".equals(value)) {
            setText(VisibilityUtil.toPresentableText(value));
          }
        }
      };
      myCheckClassesCheckBox.setRenderer(renderer);
      myCheckMethodsCheckBox.setRenderer(renderer);
      myCheckFieldsCheckBox.setRenderer(renderer);
      myCheckParametersCheckBox.setRenderer(renderer);

      myCheckClassesCheckBox.setSelectedItem(getClassVisibility());
      myCheckFieldsCheckBox.setSelectedItem(getFieldVisibility());
      myCheckMethodsCheckBox.setSelectedItem(getMethodVisibility());
      myCheckParametersCheckBox.setSelectedItem(getParameterVisibility());

      final ActionListener listener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          LOCAL_VARIABLE = myCheckLocalVariablesCheckBox.isSelected();
          setIgnoreAccessors(!myCheckGettersSettersCheckBox.isSelected());
          setClassVisibility((String)myCheckClassesCheckBox.getSelectedItem());
          setFieldVisibility((String)myCheckFieldsCheckBox.getSelectedItem());
          setMethodVisibility((String)myCheckMethodsCheckBox.getSelectedItem());
          setParameterVisibility((String)myCheckParametersCheckBox.getSelectedItem());
        }
      };
      myCheckLocalVariablesCheckBox.addActionListener(listener);
      myCheckFieldsCheckBox.addActionListener(listener);
      myCheckMethodsCheckBox.addActionListener(listener);
      myCheckClassesCheckBox.addActionListener(listener);
      myCheckParametersCheckBox.addActionListener(listener);
      myCheckGettersSettersCheckBox.addActionListener(listener);
    }

    public JComponent getPanel() {
      return myPanel;
    }
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new OptionsPanel().getPanel();
  }
}
