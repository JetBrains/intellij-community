// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;
import java.util.Objects;

public class AddTypeCastFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  @SafeFieldForPreview
  private final PsiType myType;
  private final String myName;

  public AddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression) {
    this(type, expression, "add.typecast.text");
  }

  public AddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression, @PropertyKey(resourceBundle = QuickFixBundle.BUNDLE) String messageKey) {
    super(expression);
    myType = type;
    myName = QuickFixBundle.message(messageKey, type.isValid() ? type.getCanonicalText() : "");
  }

  @Override
  @NotNull
  public String getText() {
    return myName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.typecast.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return myType.isValid() &&
           !PsiType.VOID.equals(myType) &&
           PsiTypesUtil.isDenotableType(myType, startElement) &&
           PsiTypesUtil.allTypeParametersResolved(startElement, myType) &&
           BaseIntentionAction.canModify(startElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    addTypeCast(project, (PsiExpression)startElement, myType);
  }

  public static void addTypeCast(Project project, PsiExpression originalExpression, PsiType type) {
    PsiExpression typeCast = createCastExpression(originalExpression, project, type);
    originalExpression.replace(typeCast);
  }

  static PsiExpression createCastExpression(PsiExpression original, Project project, PsiType type) {
    // remove nested casts
    PsiElement expression = PsiUtil.deparenthesizeExpression(original);
    if (expression == null) return null;

    if (type.equals(PsiType.NULL)) return null;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(original.getProject());
    if (expression instanceof PsiLiteralExpression) {
      PsiExpression newLiteral = tryConvertLiteral((PsiLiteralExpression)expression, factory, type);
      if (newLiteral != null) return newLiteral;
    }
    if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
    String text = "(" + type.getCanonicalText(false) + ")value";
    PsiTypeCastExpression typeCast = (PsiTypeCastExpression)factory.createExpressionFromText(text, original);
    typeCast = (PsiTypeCastExpression)JavaCodeStyleManager.getInstance(project).shortenClassReferences(typeCast);
    typeCast = (PsiTypeCastExpression)CodeStyleManager.getInstance(project).reformat(typeCast);

    if (expression instanceof PsiConditionalExpression) {
      // we'd better cast one branch of ternary expression if we can
      PsiConditionalExpression conditional = (PsiConditionalExpression)expression.copy();
      PsiExpression thenE = conditional.getThenExpression();
      PsiExpression elseE = conditional.getElseExpression();
      PsiType thenType = thenE == null ? null : thenE.getType();
      PsiType elseType = elseE == null ? null : elseE.getType();
      if (elseType != null && thenType != null) {
        boolean replaceThen = !TypeConversionUtil.isAssignable(type, thenType);
        boolean replaceElse = !TypeConversionUtil.isAssignable(type, elseType);
        if (replaceThen != replaceElse) {
          if (replaceThen) {
            Objects.requireNonNull(typeCast.getOperand()).replace(thenE);
            thenE.replace(typeCast);
          }
          else {
            Objects.requireNonNull(typeCast.getOperand()).replace(elseE);
            elseE.replace(typeCast);
          }
          return conditional;
        }
      }
    }

    Objects.requireNonNull(typeCast.getOperand()).replace(expression);

    return typeCast;
  }

  @Nullable
  private static PsiExpression tryConvertLiteral(@NotNull PsiLiteralExpression literal,
                                                 @NotNull PsiElementFactory factory,
                                                 PsiType wantedType) {
    String newLiteral = null;
    PsiType exprType = literal.getType();
    if (PsiType.INT.equals(exprType)) {
      if (PsiType.LONG.equals(wantedType)) {
        newLiteral = literal.getText() + "L";
      }
      else if (PsiType.FLOAT.equals(wantedType)) {
        String text = literal.getText();
        if (!text.startsWith("0")) {
          newLiteral = text + "F";
        }
      }
      else if (PsiType.DOUBLE.equals(wantedType)) {
        String text = literal.getText();
        if (!text.startsWith("0")) {
          newLiteral = text + ".0";
        }
      }
    }
    if (PsiType.LONG.equals(exprType) && PsiType.INT.equals(wantedType)) {
      Object value = literal.getValue();
      if (value instanceof Long && Objects.requireNonNull(LongRangeSet.fromType(PsiType.INT)).contains((Long)value)) {
        String text = literal.getText();
        if (text.endsWith("L") || text.endsWith("l")) {
          newLiteral = text.substring(0, text.length() - 1);
        }
      }
    }
    if (PsiType.FLOAT.equals(exprType) && PsiType.DOUBLE.equals(wantedType)) {
      String text = literal.getText();
      if (text.endsWith("F") || text.endsWith("f")) {
        newLiteral = text.substring(0, text.length() - 1);
        if (!StringUtil.containsAnyChar(newLiteral, ".eEpP")) {
          newLiteral += ".0";
        }
      }
    }
    if (newLiteral != null) {
      return factory.createExpressionFromText(newLiteral, literal);
    }
    return null;
  }

  public static void registerFix(QuickFixActionRegistrar registrar,
                                 PsiExpression qualifier,
                                 PsiJavaCodeReferenceElement ref,
                                 TextRange fixRange) {
    String referenceName = ref.getReferenceName();
    if (referenceName == null) return;
    if (qualifier instanceof PsiReferenceExpression) {
      PsiElement resolve = ((PsiReferenceExpression)qualifier).resolve();
      if (resolve == null) return;
      if (resolve instanceof PsiParameter && ((PsiParameter)resolve).getTypeElement() == null) {
        PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(resolve, PsiMethodCallExpression.class);
        if (callExpression != null) {
          JavaResolveResult result = callExpression.resolveMethodGenerics();
          if (result instanceof MethodCandidateInfo && ((MethodCandidateInfo)result).getInferenceErrorMessage() != null) {
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
      registrar.register(fixRange, new AddTypeCastFix(conjunct, qualifier, "add.qualifier.typecast.text"), null);
    }
  }
}
