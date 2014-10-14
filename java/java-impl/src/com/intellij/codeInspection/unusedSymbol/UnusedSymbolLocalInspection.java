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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.PairedUnfairLocalInspectionTool;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedSymbolLocalInspection extends UnusedSymbolLocalInspectionBase implements PairedUnfairLocalInspectionTool {

  /**
   * use {@link com.intellij.codeInspection.deadCode.UnusedDeclarationInspection} instead
   */
  @Deprecated
  public UnusedSymbolLocalInspection() {
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @NonNls
  public String getID() {
    return "unused";
  }

  @Override
  public String getAlternativeID() {
    return UnusedDeclarationInspection.ALTERNATIVE_ID;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getInspectionForBatchShortName() {
    return UnusedDeclarationInspection.SHORT_NAME;
  }

  public class OptionsPanel {
    private JCheckBox myCheckLocalVariablesCheckBox;
    private JCheckBox myCheckClassesCheckBox;
    private JCheckBox myCheckFieldsCheckBox;
    private JCheckBox myCheckMethodsCheckBox;
    private JCheckBox myCheckParametersCheckBox;
    private JCheckBox myReportUnusedParametersInPublics;
    private JPanel myPanel;

    public OptionsPanel() {
      myCheckLocalVariablesCheckBox.setSelected(LOCAL_VARIABLE);
      myCheckClassesCheckBox.setSelected(CLASS);
      myCheckFieldsCheckBox.setSelected(FIELD);
      myCheckMethodsCheckBox.setSelected(METHOD);

      myCheckParametersCheckBox.setSelected(PARAMETER);
      myReportUnusedParametersInPublics.setSelected(REPORT_PARAMETER_FOR_PUBLIC_METHODS);
      myReportUnusedParametersInPublics.setEnabled(PARAMETER);

      final ActionListener listener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          LOCAL_VARIABLE = myCheckLocalVariablesCheckBox.isSelected();
          CLASS = myCheckClassesCheckBox.isSelected();
          FIELD = myCheckFieldsCheckBox.isSelected();
          METHOD = myCheckMethodsCheckBox.isSelected();

          PARAMETER = myCheckParametersCheckBox.isSelected();
          REPORT_PARAMETER_FOR_PUBLIC_METHODS = PARAMETER && myReportUnusedParametersInPublics.isSelected();
          myReportUnusedParametersInPublics.setEnabled(PARAMETER);
        }
      };
      myCheckLocalVariablesCheckBox.addActionListener(listener);
      myCheckFieldsCheckBox.addActionListener(listener);
      myCheckMethodsCheckBox.addActionListener(listener);
      myCheckClassesCheckBox.addActionListener(listener);
      myCheckParametersCheckBox.addActionListener(listener);
      myReportUnusedParametersInPublics.addActionListener(listener);
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
