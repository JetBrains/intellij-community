// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OptionalAssignedToNullInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean WARN_ON_COMPARISON = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Report comparison of Optional with null", this, "WARN_ON_COMPARISON");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        checkNulls(expression.getType(), expression.getRExpression(),
                   InspectionsBundle.message("inspection.null.value.for.optional.context.assignment"));
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0) return;
        PsiMethod method = call.resolveMethod();
        if (method == null) return;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > args.length) return;
        boolean varArgCall = MethodCallUtils.isVarArgCall(call);
        if (!varArgCall && parameters.length < args.length) return;
        for (int i = 0; i < args.length; i++) {
          PsiParameter parameter = parameters[Math.min(parameters.length - 1, i)];
          PsiType type = parameter.getType();
          if (varArgCall && i >= parameters.length - 1 && type instanceof PsiEllipsisType) {
            type = ((PsiEllipsisType)type).getComponentType();
          }
          checkNulls(type, args[i], InspectionsBundle.message("inspection.null.value.for.optional.context.parameter"));
        }
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambda) {
        PsiElement body = lambda.getBody();
        if (body instanceof PsiExpression) {
          checkNulls(LambdaUtil.getFunctionalInterfaceReturnType(lambda), (PsiExpression)body,
                     InspectionsBundle.message("inspection.null.value.for.optional.context.lambda"));
        }
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        checkNulls(PsiTypesUtil.getMethodReturnType(statement), statement.getReturnValue(),
                   InspectionsBundle.message("inspection.null.value.for.optional.context.return"));
      }

      @Override
      public void visitVariable(PsiVariable variable) {
        checkNulls(variable.getType(), variable.getInitializer(),
                   InspectionsBundle.message("inspection.null.value.for.optional.context.declaration"));
      }

      @Override
      public void visitBinaryExpression(PsiBinaryExpression binOp) {
        if (!WARN_ON_COMPARISON) return;
        PsiExpression value = ExpressionUtils.getValueComparedWithNull(binOp);
        if (value != null &&
            TypeUtils.isOptional(value.getType()) &&
            !hasSubsequentIsPresentCall(value, binOp, JavaTokenType.EQEQ.equals(binOp.getOperationTokenType()))) {
          holder.registerProblem(binOp, "Optional value is compared with null",
                                 new ReplaceWithIsPresentFix(),
                                 new SetInspectionOptionFix(OptionalAssignedToNullInspection.this, "WARN_ON_COMPARISON",
                                                            "Do not warn when comparing Optional with null", false));
        }
      }

      private boolean hasSubsequentIsPresentCall(@NotNull PsiExpression optionalExpression,
                                                 @NotNull PsiExpression previousExpression,
                                                 boolean negated) {
        PsiPolyadicExpression parent =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(previousExpression.getParent()), PsiPolyadicExpression.class);
        if (parent == null) return false;
        IElementType expectedToken = negated ? JavaTokenType.OROR : JavaTokenType.ANDAND;
        if (!parent.getOperationTokenType().equals(expectedToken)) return false;
        PsiExpression nextExpression =
          StreamEx.of(parent.getOperands()).dropWhile(op -> !PsiTreeUtil.isAncestor(op, previousExpression, false))
                  .skip(1)
                  .findFirst()
                  .orElse(null);
        nextExpression = PsiUtil.skipParenthesizedExprDown(nextExpression);
        if (nextExpression == null) return false;
        if (negated) {
          if (!BoolUtils.isNegation(nextExpression)) return false;
          nextExpression = BoolUtils.getNegated(nextExpression);
        }
        if (!(nextExpression instanceof PsiMethodCallExpression)) return false;
        PsiMethodCallExpression call = (PsiMethodCallExpression)nextExpression;
        if (!"isPresent".equals(call.getMethodExpression().getReferenceName()) || !call.getArgumentList().isEmpty()) return false;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        return qualifier != null && PsiEquivalenceUtil.areElementsEquivalent(qualifier, optionalExpression);
      }

      private void checkNulls(PsiType type, PsiExpression expression, String declaration) {
        if (expression != null && TypeUtils.isOptional(type)) {
          ExpressionUtils.nonStructuralChildren(expression).filter(ExpressionUtils::isNullLiteral)
            .forEach(nullLiteral -> register(nullLiteral, (PsiClassType)type, declaration));
        }
      }

      private void register(PsiExpression expression, PsiClassType type, String contextName) {
        holder.registerProblem(expression,
                               InspectionsBundle.message("inspection.null.value.for.optional.message", contextName),
                               new ReplaceWithEmptyOptionalFix(type));
      }
    };
  }

  private static class ReplaceWithEmptyOptionalFix implements LocalQuickFix {
    private final String myTypeName;
    private final String myTypeParameter;
    private final String myMethodName;

    public ReplaceWithEmptyOptionalFix(PsiClassType type) {
      myTypeName = type.rawType().getCanonicalText();
      PsiType[] parameters = type.getParameters();
      myTypeParameter =
        parameters.length == 1 ? "<" + GenericsUtil.getVariableTypeByExpressionType(parameters[0]).getCanonicalText() + ">" : "";
      myMethodName = myTypeName.equals("com.google.common.base.Optional") ? "absent" : "empty";
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.null.value.for.optional.fix.name",
                                       StringUtil.getShortName(myTypeName) + "." + myMethodName + "()");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.null.value.for.optional.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiExpression)) return;
      String emptyCall = myTypeName + "." + myTypeParameter + myMethodName + "()";
      PsiElement result = new CommentTracker().replaceAndRestoreComments(element, emptyCall);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
    }
  }

  private static class ReplaceWithIsPresentFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with 'isPresent()' call";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiBinaryExpression binOp = ObjectUtils.tryCast(descriptor.getStartElement(), PsiBinaryExpression.class);
      if (binOp == null) return;
      PsiExpression value = ExpressionUtils.getValueComparedWithNull(binOp);
      if (value == null || !TypeUtils.isOptional(value.getType())) return;
      CommentTracker ct = new CommentTracker();
      String negation = binOp.getOperationTokenType().equals(JavaTokenType.NE) ? "" : "!";
      ct.replaceAndRestoreComments(binOp, negation + ct.text(value, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + ".isPresent()");
    }
  }
}
