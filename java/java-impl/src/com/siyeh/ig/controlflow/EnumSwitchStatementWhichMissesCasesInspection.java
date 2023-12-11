/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.types.DfAntiConstantType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.CreateEnumMissingSwitchBranchesFix;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class EnumSwitchStatementWhichMissesCasesInspection extends AbstractBaseJavaLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean ignoreSwitchStatementsWithDefault = true;

  @NotNull
  static @InspectionMessage String buildErrorString(String enumName, Set<String> names) {
    if (names.size() == 1) {
      return InspectionGadgetsBundle
        .message("enum.switch.statement.which.misses.cases.problem.descriptor.single", enumName, names.iterator().next());
    }
    String namesString = CreateSwitchBranchesUtil.formatMissingBranches(names);
    return InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.problem.descriptor", enumName, namesString);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSwitchStatementsWithDefault", InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.option")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        processSwitchBlock(statement);
      }

      @Override
      public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
        processSwitchBlock(expression);
      }

      public void processSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
        final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(switchBlock.getExpression());
        if (expression == null) return;
        final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
        if (aClass == null || !aClass.isEnum()) return;
        Set<String> constants = StreamEx.of(aClass.getAllFields()).select(PsiEnumConstant.class).map(PsiEnumConstant::getName)
          .toCollection(LinkedHashSet::new);
        if (constants.isEmpty()) return;
        boolean hasDefault = false;
        boolean hasNull = false;
        ProblemHighlightType highlighting = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        for (PsiSwitchLabelStatementBase child : PsiTreeUtil
          .getChildrenOfTypeAsList(switchBlock.getBody(), PsiSwitchLabelStatementBase.class)) {
          hasNull |= hasMatchingNull(child);
          if (SwitchUtils.isDefaultLabel(child)) {
            hasDefault = true;
            if (ignoreSwitchStatementsWithDefault) {
              if (!isOnTheFly) return;
              highlighting = ProblemHighlightType.INFORMATION;
            }
            if (child.isDefaultCase()) {
              continue;
            }
          }
          List<PsiEnumConstant> enumConstants = SwitchUtils.findEnumConstants(child);
          if (enumConstants.isEmpty()) {
            PsiCaseLabelElementList labelElementList = child.getCaseLabelElementList();
            if (labelElementList == null ||
                !ContainerUtil.and(labelElementList.getElements(), labelElement -> isDefaultOrNull(labelElement))) {
              // Syntax error or unresolved constant: do not report anything on incomplete code
              return;
            }
          }
          for (PsiEnumConstant constant : enumConstants) {
            if (constant.getContainingClass() != aClass) {
              // Syntax error or unresolved constant: do not report anything on incomplete code
              return;
            }
            constants.remove(constant.getName());
          }
        }
        if (!hasDefault && (switchBlock instanceof PsiSwitchExpression || hasNull)) {
          // non-exhaustive switch expression: it's a compilation error
          // switch statement using any of the new features detailed in JEP 406, such as
          // matching null, must be exhaustive, otherwise it's a compilation error as well
          // and the compilation fix should be suggested instead of normal inspection
          return;
        }
        if (constants.isEmpty()) return;
        CommonDataflow.DataflowResult dataflow = CommonDataflow.getDataflowResult(expression);
        if (dataflow != null) {
          DfType type = dataflow.getDfType(expression);
          Set<?> notValues = type instanceof DfAntiConstantType ? ((DfAntiConstantType<?>)type).getNotValues() : Collections.emptySet();
          for (Object value : notValues) {
            if (value instanceof PsiEnumConstant) {
              constants.remove(((PsiEnumConstant)value).getName());
            }
          }
          Set<String> values = StreamEx.of(dataflow.getExpressionValues(expression)).select(PsiEnumConstant.class)
            .map(PsiEnumConstant::getName).toSet();
          if (!values.isEmpty()) {
            constants.retainAll(values);
          }
        }
        if (constants.isEmpty()) return;
        String message = buildErrorString(aClass.getQualifiedName(), constants);
        var fix = new CreateEnumMissingSwitchBranchesFix(switchBlock, constants);
        if (highlighting == ProblemHighlightType.INFORMATION ||
            InspectionProjectProfileManager.isInformationLevel(getShortName(), switchBlock)) {
          holder.problem(switchBlock, message).highlight(highlighting).fix(fix).register();
        }
        else {
          int length = switchBlock.getFirstChild().getTextLength();
          holder.problem(switchBlock, message).range(new TextRange(0, length)).fix(fix).register();
          if (isOnTheFly) {
            TextRange range = new TextRange(length, switchBlock.getTextLength());
            holder.problem(switchBlock, message).highlight(ProblemHighlightType.INFORMATION).range(range).fix(fix).register();
          }
        }
      }
    };
  }

  private static boolean isDefaultOrNull(@Nullable PsiCaseLabelElement labelElement) {
    return labelElement instanceof PsiDefaultCaseLabelElement ||
           labelElement instanceof PsiExpression expr && ExpressionUtils.isNullLiteral(expr);
  }

  private static boolean hasMatchingNull(@NotNull PsiSwitchLabelStatementBase label) {
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    return labelElementList != null &&
           ContainerUtil.exists(labelElementList.getElements(),
                                el -> el instanceof PsiExpression expr && ExpressionUtils.isNullLiteral(expr));
  }
}
