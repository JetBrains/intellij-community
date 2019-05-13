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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class UnusedSymbolLocalInspection extends UnusedSymbolLocalInspectionBase {

  /**
   * use {@link com.intellij.codeInspection.deadCode.UnusedDeclarationInspection} instead
   */
  @Deprecated
  public UnusedSymbolLocalInspection() {
  }

  public class OptionsPanel {
    private JCheckBox myCheckLocalVariablesCheckBox;
    private JCheckBox myCheckClassesCheckBox;
    private JCheckBox myCheckFieldsCheckBox;
    private JCheckBox myCheckMethodsCheckBox;
    private JCheckBox myCheckParametersCheckBox;
    private JCheckBox myAccessors;
    private JPanel myPanel;
    private JLabel myClassVisibilityCb;
    private JLabel myFieldVisibilityCb;
    private JLabel myMethodVisibilityCb;
    private JLabel myMethodParameterVisibilityCb;
    private JCheckBox myInnerClassesCheckBox;
    private JLabel myInnerClassVisibilityCb;

    public OptionsPanel() {
      myCheckLocalVariablesCheckBox.setSelected(LOCAL_VARIABLE);
      myCheckClassesCheckBox.setSelected(CLASS);
      myCheckFieldsCheckBox.setSelected(FIELD);
      myCheckMethodsCheckBox.setSelected(METHOD);
      myInnerClassesCheckBox.setSelected(INNER_CLASS);
      myCheckParametersCheckBox.setSelected(PARAMETER);
      myAccessors.setSelected(!isIgnoreAccessors());
      updateEnableState();

      final ActionListener listener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          LOCAL_VARIABLE = myCheckLocalVariablesCheckBox.isSelected();
          CLASS = myCheckClassesCheckBox.isSelected();
          INNER_CLASS = myInnerClassesCheckBox.isSelected();
          FIELD = myCheckFieldsCheckBox.isSelected();
          METHOD = myCheckMethodsCheckBox.isSelected();
          setIgnoreAccessors(!myAccessors.isSelected());
          PARAMETER = myCheckParametersCheckBox.isSelected();

          updateEnableState();
        }
      };
      myCheckLocalVariablesCheckBox.addActionListener(listener);
      myCheckFieldsCheckBox.addActionListener(listener);
      myCheckMethodsCheckBox.addActionListener(listener);
      myCheckClassesCheckBox.addActionListener(listener);
      myCheckParametersCheckBox.addActionListener(listener);
      myInnerClassesCheckBox.addActionListener(listener);
      myAccessors.addActionListener(listener);
     }

    private void updateEnableState() {
      UIUtil.setEnabled(myClassVisibilityCb, CLASS, true);
      UIUtil.setEnabled(myInnerClassVisibilityCb, INNER_CLASS, true);
      UIUtil.setEnabled(myFieldVisibilityCb, FIELD, true);
      UIUtil.setEnabled(myMethodVisibilityCb, METHOD, true);
      UIUtil.setEnabled(myMethodParameterVisibilityCb, PARAMETER, true);
      myAccessors.setEnabled(METHOD);
    }

    public JComponent getPanel() {
      return myPanel;
    }

    private void createUIComponents() {
      myClassVisibilityCb = new VisibilityModifierChooser(() -> CLASS,
                                                          myClassVisibility,
                                                          modifier -> setClassVisibility(modifier),
                                                          new String[]{PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC});

      myInnerClassVisibilityCb = new VisibilityModifierChooser(() -> INNER_CLASS,
                                                               myInnerClassVisibility,
                                                               modifier -> setInnerClassVisibility(modifier));

      myFieldVisibilityCb = new VisibilityModifierChooser(() -> FIELD,
                                                          myFieldVisibility,
                                                          modifier -> setFieldVisibility(modifier));

      myMethodVisibilityCb = new VisibilityModifierChooser(() -> METHOD,
                                                           myMethodVisibility,
                                                           modifier -> setMethodVisibility(modifier));

      myMethodParameterVisibilityCb = new VisibilityModifierChooser(() -> PARAMETER,
                                                                    myParameterVisibility,
                                                                    modifier -> setParameterVisibility(modifier));

      myAccessors = new JCheckBox() {
        @Override
        public void setEnabled(boolean b) {
          super.setEnabled(b && METHOD);
        }
      };
    }
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new OptionsPanel().getPanel();
  }
}
