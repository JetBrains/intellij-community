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

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.ex.PairedUnfairLocalInspectionTool;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.psi.PsiModifierListOwner;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedSymbolLocalInspection extends BaseJavaLocalInspectionTool implements PairedUnfairLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME;
  @NonNls public static final String DISPLAY_NAME = HighlightInfoType.UNUSED_SYMBOL_DISPLAY_NAME;

  public boolean LOCAL_VARIABLE = true;
  public boolean FIELD = true;
  public boolean METHOD = true;
  public boolean CLASS = true;
  public boolean PARAMETER = true;
  public boolean REPORT_PARAMETER_FOR_PUBLIC_METHODS = true;


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
    return HighlightInfoType.UNUSED_SYMBOL_ID;
  }

  @Override
  public String getAlternativeID() {
    return "unused";
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
    private JPanel myAnnos;
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
      Project project = ProjectUtil.guessCurrentProject(myPanel);
      myAnnos.add(EntryPointsManager.getInstance(project).createConfigureAnnotationsBtn(),
                  new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                         new Insets(10, 0, 0, 0), 0, 0));
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

  public static IntentionAction createQuickFix(@NonNls String qualifiedName, @Nls String element, Project project) {
    final EntryPointsManagerBase entryPointsManager = EntryPointsManagerBase.getInstance(project);
    return SpecialAnnotationsUtil.createAddToSpecialAnnotationsListIntentionAction(
      QuickFixBundle.message("fix.unused.symbol.injection.text", element, qualifiedName),
      QuickFixBundle.message("fix.unused.symbol.injection.family"),
      entryPointsManager.ADDITIONAL_ANNOTATIONS, qualifiedName);
  }

  public static boolean isInjected(final PsiModifierListOwner modifierListOwner) {
    return EntryPointsManagerBase.getInstance(modifierListOwner.getProject()).isEntryPoint(modifierListOwner);
  }
}
