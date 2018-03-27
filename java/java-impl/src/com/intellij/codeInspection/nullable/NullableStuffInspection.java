/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NullableStuffInspection extends NullableStuffInspectionBase {
  @Override
  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return new NavigateToNullLiteralArguments(parameter);
  }

  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private class OptionsPanel extends JPanel {
    private JCheckBox myBreakingOverriding;
    private JCheckBox myNAMethodOverridesNN;
    private JPanel myPanel;
    private JCheckBox myReportNotAnnotatedGetter;
    private JButton myConfigureAnnotationsButton;
    private JCheckBox myIgnoreExternalSuperNotNull;
    private JCheckBox myNNParameterOverridesNA;
    private JCheckBox myRequireNNFieldsInitialized;
    private JBCheckBox myReportNullLiteralsPassedNotNullParameter;

    private OptionsPanel() {
      super(new BorderLayout());
      add(myPanel, BorderLayout.CENTER);

      ActionListener actionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          apply();
        }
      };
      myNAMethodOverridesNN.addActionListener(actionListener);
      myBreakingOverriding.addActionListener(actionListener);
      myNNParameterOverridesNA.addActionListener(actionListener);
      myReportNotAnnotatedGetter.addActionListener(actionListener);
      myIgnoreExternalSuperNotNull.addActionListener(actionListener);
      myRequireNNFieldsInitialized.addActionListener(actionListener);
      myReportNullLiteralsPassedNotNullParameter.addActionListener(actionListener);
      myConfigureAnnotationsButton.addActionListener(NullableNotNullDialog.createActionListener(this));
      reset();
    }

    private void reset() {
      myBreakingOverriding.setSelected(REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE);
      myNAMethodOverridesNN.setSelected(REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL);
      myReportNotAnnotatedGetter.setSelected(REPORT_NOT_ANNOTATED_GETTER);
      myIgnoreExternalSuperNotNull.setSelected(IGNORE_EXTERNAL_SUPER_NOTNULL);
      myNNParameterOverridesNA.setSelected(REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED);
      myRequireNNFieldsInitialized.setSelected(REQUIRE_NOTNULL_FIELDS_INITIALIZED);
      myReportNullLiteralsPassedNotNullParameter.setSelected(REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER);

      myIgnoreExternalSuperNotNull.setEnabled(myNAMethodOverridesNN.isSelected());
    }

    private void apply() {
      REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = myNAMethodOverridesNN.isSelected();
      REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = myBreakingOverriding.isSelected();
      REPORT_NOT_ANNOTATED_GETTER = myReportNotAnnotatedGetter.isSelected();
      IGNORE_EXTERNAL_SUPER_NOTNULL = myIgnoreExternalSuperNotNull.isSelected();
      REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = myNNParameterOverridesNA.isSelected();
      REQUIRE_NOTNULL_FIELDS_INITIALIZED = myRequireNNFieldsInitialized.isSelected();
      REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = myReportNullLiteralsPassedNotNullParameter.isSelected();
      REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL;

      myIgnoreExternalSuperNotNull.setEnabled(myNAMethodOverridesNN.isSelected());
    }
  }

  public static class NavigateToNullLiteralArguments extends LocalQuickFixOnPsiElement {
    public NavigateToNullLiteralArguments(@NotNull PsiParameter element) {
      super(element);
    }

    @NotNull
    @Override
    public String getText() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("nullable.stuff.inspection.navigate.null.argument.usages.fix.family.name");
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      PsiParameter p = (PsiParameter)startElement;
      final PsiMethod method = PsiTreeUtil.getParentOfType(p, PsiMethod.class);
      if (method == null) return;
      final int parameterIdx = ArrayUtil.find(method.getParameterList().getParameters(), p);
      if (parameterIdx < 0) return;

      UsageViewPresentation presentation = new UsageViewPresentation();
      String title = InspectionsBundle.message("nullable.stuff.inspection.navigate.null.argument.usages.view.name", p.getName());
      presentation.setUsagesString(title);
      presentation.setTabName(title);
      presentation.setTabText(title);
      UsageViewManager.getInstance(project).searchAndShowUsages(
        new UsageTarget[]{new PsiElement2UsageTargetAdapter(method.getParameterList().getParameters()[parameterIdx])},
        () -> new UsageSearcher() {
          @Override
          public void generate(@NotNull final Processor<Usage> processor) {
            ReadAction.run(() -> JavaNullMethodArgumentUtil.searchNullArgument(method, parameterIdx, (arg) -> processor.process(new UsageInfo2UsageAdapter(new UsageInfo(arg)))));
          }
        }, false, false, presentation, null);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  };
}
