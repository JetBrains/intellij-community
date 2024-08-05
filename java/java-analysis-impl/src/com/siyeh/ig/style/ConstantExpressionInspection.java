// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public final class ConstantExpressionInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final int MAX_RESULT_LENGTH_TO_DISPLAY = 40;
  private static final int MAX_EXPRESSION_LENGTH = 200;

  public boolean skipIfContainsReferenceExpression = false;
  public boolean reportOnlyCompileTimeConstants = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.checkbox("skipIfContainsReferenceExpression",
                       InspectionGadgetsBundle.message("inspection.constant.expression.skip.non.literal"))
        .description(InspectionGadgetsBundle.message("inspection.constant.expression.skip.non.literal.description")),
      OptPane.checkbox("reportOnlyCompileTimeConstants",
                       InspectionGadgetsBundle.message("inspection.constant.expression.report.compile.time"))
        .description(InspectionGadgetsBundle.message("inspection.constant.expression.report.compile.time.description")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        handle(expression);
      }
      
      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        handle(expression);
      }
      
      private void handle(@NotNull PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression) return;
        // inspection disabled for long expressions because of performance issues on
        // relatively common large string expressions.
        Object value = computeConstant(expression);
        if (value == null || value instanceof Enum<?>) return;
        if (value instanceof PsiField && !(value instanceof PsiEnumConstant)) return;
        if (value instanceof PsiElement e && expression instanceof PsiReferenceExpression ref && ref.isReferenceTo(e)) return;
        String valueText = getValueText(value);
        if (valueText == null || expression.textMatches(valueText)) return;
        String message = valueText.length() > MAX_RESULT_LENGTH_TO_DISPLAY ?
                         InspectionGadgetsBundle.message("inspection.constant.expression.display.name") :
                         InspectionGadgetsBundle.message("inspection.constant.expression.message", valueText);
        if (skipIfContainsReferenceExpression && hasReferences(expression)) {
          if (isOnTheFly) {
            holder.registerProblem(expression, message, ProblemHighlightType.INFORMATION,
                                   new ComputeConstantValueFix(expression, valueText));
          }
        }
        else {
          holder.registerProblem(expression, message,
                                 new ComputeConstantValueFix(expression, valueText));
        }
      }

      @Nullable
      private Object computeConstant(PsiExpression expression) {
        if (expression.getTextLength() > MAX_EXPRESSION_LENGTH) return null;
        if (expression.getType() == null) return null;
        Object value = computeValue(expression);
        if (value == null) return null;
        final PsiExpression parent = getParentExpression(expression);
        if (parent != null && computeValue(parent) != null) return null;
        return value;
      }

      private Object computeValue(PsiExpression expression) {
        if (expression instanceof PsiClassObjectAccessExpression) return null;
        try {
          Object value = ExpressionUtils.computeConstantExpression(expression, true);
          if (value != null) {
            return value;
          }
        }
        catch (ConstantEvaluationOverflowException ignore) {
          return null;
        }
        if (reportOnlyCompileTimeConstants) return null;
        if (SideEffectChecker.mayHaveSideEffects(expression)) return null;
        return CommonDataflow.computeValue(expression);
      }

      @Nullable
      private static PsiExpression getParentExpression(PsiExpression expression) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiExpressionList || parent instanceof PsiTemplate) {
          parent = parent.getParent();
        }
        return tryCast(parent, PsiExpression.class);
      }

      private static boolean hasReferences(@NotNull PsiExpression expression) {
        return expression instanceof PsiReferenceExpression || 
               PsiTreeUtil.getChildOfType(expression, PsiReferenceExpression.class) != null;
      }
    };
  }

  private static class ComputeConstantValueFix extends PsiUpdateModCommandQuickFix {
    private final String myText;
    private final String myValueText;

    ComputeConstantValueFix(PsiExpression expression, String valueText) {
      myText = PsiExpressionTrimRenderer.render(expression);
      myValueText = valueText;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      if (myText.length() < MAX_RESULT_LENGTH_TO_DISPLAY) {
        return InspectionGadgetsBundle.message("inspection.constant.expression.fix.name", myText);
      }
      return InspectionGadgetsBundle.message("inspection.constant.expression.fix.name.short");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.constant.expression.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiExpression expression = (PsiExpression)element;
      PsiReplacementUtil.replaceExpression(expression, myValueText, new CommentTracker());
    }
  }

  private static String getValueText(Object value) {
    @NonNls final String newExpression;
    if (value instanceof String string) {
      newExpression = '"' + StringUtil.escapeStringCharacters(string) + '"';
    }
    else if (value instanceof Character) {
      newExpression = '\'' + StringUtil.escapeStringCharacters(value.toString()) + '\'';
    }
    else if (value instanceof Long) {
      newExpression = value.toString() + 'L';
    }
    else if (value instanceof Double) {
      final double v = ((Double)value).doubleValue();
      if (Double.isNaN(v)) {
        newExpression = "java.lang.Double.NaN";
      }
      else if (Double.isInfinite(v)) {
        if (v > 0.0) {
          newExpression = "java.lang.Double.POSITIVE_INFINITY";
        }
        else {
          newExpression = "java.lang.Double.NEGATIVE_INFINITY";
        }
      }
      else {
        newExpression = Double.toString(v);
      }
    }
    else if (value instanceof Float) {
      final float v = ((Float)value).floatValue();
      if (Float.isNaN(v)) {
        newExpression = "java.lang.Float.NaN";
      }
      else if (Float.isInfinite(v)) {
        if (v > 0.0F) {
          newExpression = "java.lang.Float.POSITIVE_INFINITY";
        }
        else {
          newExpression = "java.lang.Float.NEGATIVE_INFINITY";
        }
      }
      else {
        newExpression = Float.toString(v) + 'f';
      }
    }
    else if (value == null) {
      newExpression = "null";
    }
    else if (value instanceof PsiField field) {
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) return null;
      return containingClass.getQualifiedName() + "." + field.getName();
    }
    else if (value instanceof PsiType) {
      if (value instanceof PsiClassType clsType) {
        return clsType.getCanonicalText() + ".class";
      }
      return null;
    }
    else {
      newExpression = String.valueOf(value);
    }
    return newExpression;
  }
}
