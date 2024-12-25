// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class AddTypeCastFix extends PsiUpdateModCommandAction<PsiExpression> {
  private final PsiType myType;
  private final @IntentionName String myName;

  public AddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression) {
    this(type, expression, null);
  }

  public AddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression, @Nls @Nullable String role) {
    super(expression);
    boolean literalConversion = tryConvertNumericLiteral(expression, type) != null;
    if (role == null) {
      role = QuickFixBundle.message(literalConversion ? "fix.expression.role.literal" : "fix.expression.role.expression");
    }
    myType = type;
    myName = QuickFixBundle.message(literalConversion ? "add.typecast.convert.text" : "add.typecast.cast.text",
                                    type.isValid() ? type.getCanonicalText() : "", role);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.typecast.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression expr) {
    if (!myType.isValid() ||
        PsiTypes.voidType().equals(myType) ||
        !PsiTypesUtil.isDenotableType(myType, expr) ||
        !PsiTypesUtil.allTypeParametersResolved(expr, myType)) return null;
    return Presentation.of(myName).withPriority(PriorityAction.Priority.HIGH).withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression expression, @NotNull ModPsiUpdater updater) {
    addTypeCast(context.project(), expression, myType);
  }

  public static void addTypeCast(Project project, PsiExpression originalExpression, PsiType type) {
    PsiExpression typeCast = createCastExpression(originalExpression, type);
    originalExpression.replace(Objects.requireNonNull(typeCast));
  }

  static String tryConvertNumericLiteral(PsiElement expr, @NotNull PsiType type) {
    return expr instanceof PsiLiteralExpression literal ? PsiLiteralUtil.tryConvertNumericLiteral(literal, type) : null;
  }

  static PsiExpression createCastExpression(PsiExpression original, PsiType type) {
    // remove nested casts
    PsiElement expression = PsiUtil.deparenthesizeExpression(original);
    if (expression == null) return null;

    if (type.equals(PsiTypes.nullType())) return null;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(original.getProject());
    String newLiteral = tryConvertNumericLiteral(expression, type);
    if (newLiteral != null) {
      return factory.createExpressionFromText(newLiteral, null);
    }
    if (type instanceof PsiEllipsisType ellipsisType) type = ellipsisType.toArrayType();
    String text = "(" + type.getCanonicalText(false) + ")value";
    PsiTypeCastExpression typeCast = (PsiTypeCastExpression)factory.createExpressionFromText(text, original);

    PsiExpression operand = typeCast.getOperand();
    assert operand != null;
    if (expression instanceof PsiConditionalExpression) {
      // we'd better cast one branch of ternary expression if we can
      PsiConditionalExpression conditional = (PsiConditionalExpression)expression.copy();
      PsiExpression thenExpression = conditional.getThenExpression();
      PsiExpression elseExpression = conditional.getElseExpression();
      if (thenExpression != null && elseExpression != null) {
        boolean replaceThen = !TypeConversionUtil.areTypesAssignmentCompatible(type, thenExpression);
        boolean replaceElse = !TypeConversionUtil.areTypesAssignmentCompatible(type, elseExpression);
        if (replaceThen != replaceElse) {
          if (replaceThen) {
            operand.replace(thenExpression);
            thenExpression.replace(typeCast);
          }
          else {
            operand.replace(elseExpression);
            elseExpression.replace(typeCast);
          }
          return conditional;
        }
      }
    }

    operand.replace(expression);

    return typeCast;
  }

  public static void registerFix(QuickFixActionRegistrar registrar,
                                 PsiExpression qualifier,
                                 PsiJavaCodeReferenceElement ref,
                                 TextRange fixRange) {
    String referenceName = ref.getReferenceName();
    if (referenceName == null) return;
    if (qualifier instanceof PsiReferenceExpression referenceExpression) {
      PsiElement resolve = referenceExpression.resolve();
      if (resolve == null) return;
      if (resolve instanceof PsiParameter parameter && parameter.getTypeElement() == null) {
        PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(resolve, PsiMethodCallExpression.class);
        if (callExpression != null) {
          JavaResolveResult result = callExpression.resolveMethodGenerics();
          if (result instanceof MethodCandidateInfo candidateInfo && candidateInfo.getInferenceErrorMessage() != null) {
            return;
          }
        }
      }
    }
    PsiElement gParent = ref.getParent();
    List<PsiType> conjuncts = GuessManager.getInstance(qualifier.getProject()).getControlFlowExpressionTypeConjuncts(qualifier);
    for (PsiType conjunct : conjuncts) {
      PsiClass psiClass = PsiUtil.resolveClassInType(conjunct);
      if (psiClass == null) continue;
      if (gParent instanceof PsiMethodCallExpression) {
        if (psiClass.findMethodsByName(referenceName).length == 0) {
          continue;
        }
      }
      else if (psiClass.findFieldByName(referenceName, true) == null) {
        continue;
      }
      registrar.register(fixRange, new AddTypeCastFix(conjunct, qualifier, QuickFixBundle.message("fix.expression.role.qualifier")).asIntention(), null);
    }
  }
}
