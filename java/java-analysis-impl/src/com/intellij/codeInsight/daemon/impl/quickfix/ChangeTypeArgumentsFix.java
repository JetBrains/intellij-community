// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ChangeTypeArgumentsFix extends PsiUpdateModCommandAction<PsiNewExpression> {
  private static final Logger LOG = Logger.getInstance(ChangeTypeArgumentsFix.class);

  private final @NotNull PsiMethod myTargetMethod;
  private final @NotNull PsiClass myPsiClass;
  private final @NotNull PsiExpression @NotNull [] myExpressions;

  ChangeTypeArgumentsFix(@NotNull PsiMethod targetMethod,
                         @NotNull PsiClass psiClass,
                         @NotNull PsiExpression @NotNull [] expressions,
                         @NotNull PsiNewExpression newExpression) {
    super(newExpression);
    myTargetMethod = targetMethod;
    myPsiClass = psiClass;
    myExpressions = expressions;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("change.type.arguments");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiNewExpression newExpression) {
    if (!myPsiClass.isValid() || !myTargetMethod.isValid()) return null;
    final PsiTypeParameter[] typeParameters = myPsiClass.getTypeParameters();
    if (typeParameters.length == 0 || newExpression.getArgumentList() == null) return null;
    final PsiJavaCodeReferenceElement reference = newExpression.getClassOrAnonymousClassReference();
    if (reference == null) return null;
    final PsiReferenceParameterList parameterList = reference.getParameterList();
    if (parameterList == null) return null;
    final PsiSubstitutor substitutor = inferTypeArguments(newExpression);
    final PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
    if (parameters.length != myExpressions.length) return null;
    for (int i = 0, length = parameters.length; i < length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType expectedType = substitutor.substitute(parameter.getType());
      if (!myExpressions[i].isValid()) return null;
      final PsiType actualType = myExpressions[i].getType();
      if (expectedType == null || actualType == null || !TypeConversionUtil.isAssignable(expectedType, actualType)) return null;
    }
    for (PsiTypeParameter parameter : typeParameters) {
      if (substitutor.substitute(parameter) == null) return null;
    }
    String typeParametersText = StringUtil.join(myPsiClass.getTypeParameters(), typeParameter -> {
      final PsiType substituted = substitutor.substitute(typeParameter);
      return substituted != null ? substituted.getPresentableText() : CommonClassNames.JAVA_LANG_OBJECT;
    }, ", ");
    return Presentation.of(JavaAnalysisBundle.message("change.type.arguments.to.0", typeParametersText))
      .withPriority(PriorityAction.Priority.HIGH)
      .withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiNewExpression newExpression, @NotNull ModPsiUpdater updater) {
    final PsiTypeParameter[] typeParameters = myPsiClass.getTypeParameters();
    final PsiSubstitutor psiSubstitutor = inferTypeArguments(newExpression);
    final PsiJavaCodeReferenceElement reference = newExpression.getClassOrAnonymousClassReference();
    LOG.assertTrue(reference != null, newExpression);
    final PsiReferenceParameterList parameterList = reference.getParameterList();
    LOG.assertTrue(parameterList != null, newExpression);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    PsiTypeElement[] elements = parameterList.getTypeParameterElements();
    for (int i = elements.length - 1; i >= 0; i--) {
      PsiType typeArg = Objects.requireNonNull(psiSubstitutor.substitute(typeParameters[i]));
      PsiElement replaced = elements[i].replace(factory.createTypeElement(typeArg));
      JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(replaced);
    }
  }

  private PsiSubstitutor inferTypeArguments(@NotNull PsiNewExpression newExpression) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(newExpression.getProject());
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    LOG.assertTrue(argumentList != null);
    final PsiExpression[] expressions = argumentList.getExpressions();
    return resolveHelper.inferTypeArguments(myPsiClass.getTypeParameters(), parameters, expressions,
                                            PsiSubstitutor.EMPTY,
                                            newExpression.getParent(),
                                            DefaultParameterTypeInferencePolicy.INSTANCE);
  }


  public static void registerIntentions(JavaResolveResult @NotNull [] candidates,
                                        @NotNull PsiExpressionList list,
                                        @NotNull HighlightInfo.Builder highlightInfo,
                                        PsiClass psiClass, TextRange fixRange) {
    if (candidates.length == 0) return;
    PsiExpression[] expressions = list.getExpressions();
    for (JavaResolveResult candidate : candidates) {
      registerIntention(expressions, highlightInfo, psiClass, candidate, list, fixRange);
    }
  }

  private static void registerIntention(PsiExpression @NotNull [] expressions,
                                        @NotNull HighlightInfo.Builder builder,
                                        PsiClass psiClass,
                                        @NotNull JavaResolveResult candidate,
                                        @NotNull PsiElement context,
                                        TextRange fixRange) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    if (method != null && BaseIntentionAction.canModify(method)) {
      PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(context, PsiNewExpression.class);
      if (newExpression == null) return;
      final ChangeTypeArgumentsFix fix = new ChangeTypeArgumentsFix(method, psiClass, expressions, newExpression);
      builder.registerFix(fix, null, null, fixRange, null);
    }
  }
}
