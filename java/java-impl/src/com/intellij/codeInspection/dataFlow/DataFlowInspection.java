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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ui.JBUI;
import com.siyeh.ig.fixes.IntroduceVariableFix;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DataFlowInspection extends DataFlowInspectionBase {
  @Override
  protected void addSurroundWithIfFix(PsiExpression qualifier, List<LocalQuickFix> fixes, boolean onTheFly) {
    if (onTheFly && SurroundWithIfFix.isAvailable(qualifier)) {
      fixes.add(new SurroundWithIfFix(qualifier));
    }
  }

  @Override
  protected LocalQuickFix[] createConditionalAssignmentFixes(boolean evaluatesToTrue, PsiAssignmentExpression assignment, final boolean onTheFly) {
    IElementType op = assignment.getOperationTokenType();
    boolean toRemove = op == JavaTokenType.ANDEQ && !evaluatesToTrue || op == JavaTokenType.OREQ && evaluatesToTrue;
    if (toRemove && !onTheFly) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    return new LocalQuickFix[]{toRemove ? new RemoveAssignmentFix() : createSimplifyToAssignmentFix()};
  }

  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @Override
  protected AddAssertStatementFix createAssertFix(PsiBinaryExpression binary, PsiExpression expression) {
    return RefactoringUtil.getParentStatement(expression, false) == null ? null : new AddAssertStatementFix(binary);
  }

  @Override
  protected LocalQuickFix createReplaceWithTrivialLambdaFix(Object value) {
    return new ReplaceWithTrivialLambdaFix(value);
  }

  @Override
  protected LocalQuickFix createIntroduceVariableFix(final PsiExpression expression) {
    return new IntroduceVariableFix(true);
  }

  @Override
  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return new NullableStuffInspection.NavigateToNullLiteralArguments(parameter);
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myIgnoreAssertions;
    private final JCheckBox myReportConstantReferences;
    private final JCheckBox mySuggestNullables;
    private final JCheckBox myDontReportTrueAsserts;
    private final JCheckBox myTreatUnknownMembersAsNullable;
    private final JCheckBox myReportNullArguments;
    private final JCheckBox myReportNullableMethodsReturningNotNull;
    private final JCheckBox myReportUncheckedOptionals;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      mySuggestNullables = new JCheckBox(
        InspectionsBundle.message("inspection.data.flow.nullable.quickfix.option"));
      mySuggestNullables.setSelected(SUGGEST_NULLABLE_ANNOTATIONS);
      mySuggestNullables.getModel().addItemListener(e -> SUGGEST_NULLABLE_ANNOTATIONS = mySuggestNullables.isSelected());

      myDontReportTrueAsserts = new JCheckBox(
        InspectionsBundle.message("inspection.data.flow.true.asserts.option"));
      myDontReportTrueAsserts.setSelected(DONT_REPORT_TRUE_ASSERT_STATEMENTS);
      myDontReportTrueAsserts.getModel().addItemListener(e -> DONT_REPORT_TRUE_ASSERT_STATEMENTS = myDontReportTrueAsserts.isSelected());
      
      myIgnoreAssertions = new JCheckBox("Ignore assert statements");
      myIgnoreAssertions.setSelected(IGNORE_ASSERT_STATEMENTS);
      myIgnoreAssertions.getModel().addItemListener(e -> IGNORE_ASSERT_STATEMENTS = myIgnoreAssertions.isSelected());

      myReportConstantReferences = new JCheckBox("Warn when reading a value guaranteed to be constant");
      myReportConstantReferences.setSelected(REPORT_CONSTANT_REFERENCE_VALUES);
      myReportConstantReferences.getModel().addItemListener(
        e -> REPORT_CONSTANT_REFERENCE_VALUES = myReportConstantReferences.isSelected());

      myTreatUnknownMembersAsNullable = new JCheckBox("Treat non-annotated members and parameters as @Nullable");
      myTreatUnknownMembersAsNullable.setSelected(TREAT_UNKNOWN_MEMBERS_AS_NULLABLE);
      myTreatUnknownMembersAsNullable.getModel().addItemListener(
        e -> TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = myTreatUnknownMembersAsNullable.isSelected());

      myReportNullArguments = new JCheckBox("Report not-null required parameter with null-literal argument usages");
      myReportNullArguments.setSelected(REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER);
      myReportNullArguments.getModel().addItemListener(e -> REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = myReportNullArguments.isSelected());

      myReportNullableMethodsReturningNotNull = new JCheckBox("Report nullable methods that always return a non-null value");
      myReportNullableMethodsReturningNotNull.setSelected(REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL);
      myReportNullableMethodsReturningNotNull.getModel().addItemListener(
        e -> REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = myReportNullableMethodsReturningNotNull.isSelected());

      myReportUncheckedOptionals = new JCheckBox("Report Optional.get() calls without previous isPresent check");
      myReportUncheckedOptionals.setSelected(REPORT_UNCHECKED_OPTIONALS);
      myReportUncheckedOptionals.getModel().addItemListener(e -> REPORT_UNCHECKED_OPTIONALS = myReportUncheckedOptionals.isSelected());

      gc.insets = JBUI.emptyInsets();
      gc.gridy = 0;
      add(mySuggestNullables, gc);

      final JButton configureAnnotations = NullableNotNullDialog.createConfigureAnnotationsButton(this);
      gc.gridy++;
      gc.fill = GridBagConstraints.NONE;
      gc.insets.left = 20;
      gc.insets.bottom = 15;
      add(configureAnnotations, gc);

      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weighty = 1;
      gc.insets.left = 0;
      gc.gridy++;
      add(myDontReportTrueAsserts, gc);

      gc.gridy++;
      add(myIgnoreAssertions, gc);

      gc.gridy++;
      add(myReportConstantReferences, gc);

      gc.gridy++;
      add(myTreatUnknownMembersAsNullable, gc);

      gc.gridy++;
      add(myReportNullArguments, gc);

      gc.gridy++;
      add(myReportNullableMethodsReturningNotNull, gc);

      gc.gridy++;
      add(myReportUncheckedOptionals, gc);
    }
  }

}