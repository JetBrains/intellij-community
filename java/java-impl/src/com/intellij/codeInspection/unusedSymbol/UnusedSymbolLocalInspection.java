// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.psi.PsiModifier;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class UnusedSymbolLocalInspection extends UnusedSymbolLocalInspectionBase {

  /**
   * @deprecated use {@link com.intellij.codeInspection.deadCode.UnusedDeclarationInspection} instead
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
    private JCheckBox myCheckParameterExcludingHierarchyCheckBox;
    private JCheckBox myInnerClassesCheckBox;
    private JLabel myInnerClassVisibilityCb;

    public OptionsPanel() {
      myCheckLocalVariablesCheckBox.setSelected(LOCAL_VARIABLE);
      myCheckClassesCheckBox.setSelected(CLASS);
      myCheckFieldsCheckBox.setSelected(FIELD);
      myCheckMethodsCheckBox.setSelected(METHOD);
      myInnerClassesCheckBox.setSelected(INNER_CLASS);
      myCheckParametersCheckBox.setSelected(PARAMETER);
      myCheckParameterExcludingHierarchyCheckBox.setSelected(myCheckParameterExcludingHierarchy);
      myCheckParameterExcludingHierarchyCheckBox.setBorder(JBUI.Borders.emptyLeft(5));
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
          setCheckParameterExcludingHierarchy(myCheckParameterExcludingHierarchyCheckBox.isSelected());

          updateEnableState();
        }
      };
      myCheckLocalVariablesCheckBox.addActionListener(listener);
      myCheckFieldsCheckBox.addActionListener(listener);
      myCheckMethodsCheckBox.addActionListener(listener);
      myCheckClassesCheckBox.addActionListener(listener);
      myCheckParametersCheckBox.addActionListener(listener);
      myCheckParameterExcludingHierarchyCheckBox.addActionListener(listener);
      myInnerClassesCheckBox.addActionListener(listener);
      myAccessors.addActionListener(listener);
     }

    private void updateEnableState() {
      UIUtil.setEnabled(myClassVisibilityCb, CLASS, true);
      UIUtil.setEnabled(myInnerClassVisibilityCb, INNER_CLASS, true);
      UIUtil.setEnabled(myFieldVisibilityCb, FIELD, true);
      UIUtil.setEnabled(myMethodVisibilityCb, METHOD, true);
      UIUtil.setEnabled(myMethodParameterVisibilityCb, PARAMETER, true);
      setEnabledExcludingHierarchyCheckbox(getParameterVisibility());
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
                                                                    modifier -> {
                                                                      setParameterVisibility(modifier);
                                                                      setEnabledExcludingHierarchyCheckbox(modifier);
                                                                    });

      myAccessors = new JCheckBox() {
        @Override
        public void setEnabled(boolean b) {
          super.setEnabled(b && METHOD);
        }
      };
    }

    private void setEnabledExcludingHierarchyCheckbox(@Nullable String modifier) {
      myCheckParameterExcludingHierarchyCheckBox.setVisible(!PsiModifier.PRIVATE.equals(modifier));
    }
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new OptionsPanel().getPanel();
  }
}
