// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class StringRepeatCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher APPEND = CallMatcher.instanceCall("java.lang.AbstractStringBuilder", "append").parameterCount(1);

  public boolean ADD_MATH_MAX = true;
  
  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Add Math.max(0, count) to avoid possible semantics change", this, "ADD_MATH_MAX");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel11OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(PsiForStatement statement) {
        PsiMethodCallExpression call = findAppendCall(statement);
        if (call == null) return;
        PsiReferenceExpression qualifier = tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()),
                                                   PsiReferenceExpression.class);
        if (qualifier == null || !ExpressionUtil.isEffectivelyUnqualified(qualifier)) return;
        CountingLoop loop = CountingLoop.from(statement);
        if (loop == null) return;
        PsiLocalVariable var = loop.getCounter();
        if (var.getType().equals(PsiType.LONG) || VariableAccessUtils.variableIsUsed(var, call)) return;
        PsiExpression arg = call.getArgumentList().getExpressions()[0];
        if (SideEffectChecker.mayHaveSideEffects(arg)) return;
        holder.registerProblem(statement.getFirstChild(), "Can be replaced with 'String.repeat()'",
                               new StringRepeatCanBeUsedFix(ADD_MATH_MAX));
      }
    };
  }

  @Nullable
  private static PsiMethodCallExpression findAppendCall(PsiForStatement statement) {
    PsiExpressionStatement body = tryCast(ControlFlowUtils.stripBraces(statement.getBody()), PsiExpressionStatement.class);
    if (body == null) return null;
    PsiMethodCallExpression call = tryCast(body.getExpression(), PsiMethodCallExpression.class);
    if (!APPEND.test(call)) return null;
    return call;
  }

  private static class StringRepeatCanBeUsedFix implements LocalQuickFix {
    private final boolean myAddMathMax;

    private StringRepeatCanBeUsedFix(boolean addMathMax) {
      myAddMathMax = addMathMax;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "String.repeat()");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiForStatement statement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiForStatement.class);
      if (statement == null) return;
      CountingLoop loop = CountingLoop.from(statement);
      if (loop == null) return;
      PsiMethodCallExpression call = findAppendCall(statement);
      if (call == null) return;
      PsiExpression builder = call.getMethodExpression().getQualifierExpression();
      if (builder == null) return;
      PsiExpression arg = call.getArgumentList().getExpressions()[0];
      PsiExpression from, to;
      if (loop.isDescending()) {
        from = loop.getBound();
        to = loop.getInitializer();
      }
      else {
        from = loop.getInitializer();
        to = loop.getBound();
      }
      CommentTracker ct = new CommentTracker();
      String repeatQualifier = getRepeatQualifier(arg, ct);
      String countText = getCountText(from, to, loop.isIncluding(), ct);
      if (myAddMathMax && canBeNegative(from, to)) {
        countText = CommonClassNames.JAVA_LANG_MATH + ".max(0," + countText + ")";
      }
      String replacement = repeatQualifier + ".repeat(" + countText + ")";
      ct.replace(arg, replacement);
      ct.replaceAndRestoreComments(statement, call.getParent());
    }

    @NotNull
    private static String getCountText(PsiExpression from, PsiExpression to, boolean including, CommentTracker ct) {
      String countText = null;
      Number fromNumber = JavaPsiMathUtil.getNumberFromLiteral(from);
      if (fromNumber instanceof Integer) {
        int origin = fromNumber.intValue();
        if (origin < Integer.MAX_VALUE) {
          if (including) {
            origin--;
          }
          countText = JavaPsiMathUtil.add(to, -origin, ct);
        }
      }
      if (countText == null) {
        countText =
          ct.text(to, ParenthesesUtils.ADDITIVE_PRECEDENCE) + "-" + ct.text(from, ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE);
        if (including) {
          PsiExpression expr = JavaPsiFacade.getElementFactory(from.getProject()).createExpressionFromText(countText, from);
          countText = JavaPsiMathUtil.add(expr, 1, ct);
        }
      }
      return countText;
    }

    private static boolean canBeNegative(PsiExpression from, PsiExpression to) {
      boolean canBeNegative = true;
      CommonDataflow.DataflowResult dataflow = CommonDataflow.getDataflowResult(from);
      if (dataflow != null) {
        LongRangeSet fromRange = dataflow.getExpressionFact(from, DfaFactType.RANGE);
        LongRangeSet toRange = dataflow.getExpressionFact(to, DfaFactType.RANGE);
        if (fromRange != null && !fromRange.isEmpty() && toRange != null && !toRange.isEmpty() && fromRange.max() <= toRange.min()) {
          canBeNegative = false;
        }
      }
      return canBeNegative;
    }

    @NotNull
    private static String getRepeatQualifier(PsiExpression arg, CommentTracker ct) {
      if (arg instanceof PsiLiteralExpression && !TypeUtils.isJavaLangString(arg.getType())) {
        PsiLiteralExpression literal = (PsiLiteralExpression)arg;
        Object value = literal.getValue();
        if (value instanceof Character) {
          return PsiLiteralUtil.stringForCharLiteral(literal.getText());
        }
        return StringUtil.wrapWithDoubleQuote(StringUtil.escapeStringCharacters(String.valueOf(value)));
      }
      if (TypeUtils.isJavaLangString(arg.getType()) && NullabilityUtil.getExpressionNullability(arg, true) == Nullability.NOT_NULL) {
        return ct.text(arg, ParenthesesUtils.METHOD_CALL_PRECEDENCE);
      }
      return CommonClassNames.JAVA_LANG_STRING + ".valueOf(" + ct.text(arg) + ")";
    }
  }
}
