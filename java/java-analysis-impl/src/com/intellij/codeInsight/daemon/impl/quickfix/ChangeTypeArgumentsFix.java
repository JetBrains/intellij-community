// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class ChangeTypeArgumentsFix implements IntentionActionWithFixAllOption, HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(ChangeTypeArgumentsFix.class);

  private final @NotNull PsiMethod myTargetMethod;
  private final @NotNull PsiClass myPsiClass;
  private final PsiExpression @NotNull [] myExpressions;
  private final PsiNewExpression myNewExpression;

  ChangeTypeArgumentsFix(@NotNull PsiMethod targetMethod,
                         @NotNull PsiClass psiClass,
                         PsiExpression @NotNull [] expressions,
                         @NotNull PsiElement context) {
    this(targetMethod, psiClass, expressions, PsiTreeUtil.getParentOfType(context, PsiNewExpression.class));
  }

  private ChangeTypeArgumentsFix(@NotNull PsiMethod targetMethod,
                                 @NotNull PsiClass psiClass,
                                 PsiExpression @NotNull [] expressions,
                                 PsiNewExpression newExpression) {
    myTargetMethod = targetMethod;
    myPsiClass = psiClass;
    myExpressions = expressions;
    myNewExpression = newExpression;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new ChangeTypeArgumentsFix(myTargetMethod, myPsiClass, myExpressions,
                                      PsiTreeUtil.findSameElementInCopy(myNewExpression, target));
  }

  @Override
  @NotNull
  public String getText() {
    final PsiSubstitutor substitutor = inferTypeArguments();
    return JavaAnalysisBundle.message("change.type.arguments.to.0", StringUtil.join(myPsiClass.getTypeParameters(), typeParameter -> {
      final PsiType substituted = substitutor.substitute(typeParameter);
      return substituted != null ? substituted.getPresentableText() : CommonClassNames.JAVA_LANG_OBJECT;
    }, ", "));
  }


  @Override
  @NotNull
  public String getFamilyName() {
    return JavaAnalysisBundle.message("change.type.arguments");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myPsiClass.isValid() || !myTargetMethod.isValid()) return false;
    final PsiTypeParameter[] typeParameters = myPsiClass.getTypeParameters();
    if (typeParameters.length > 0) {
      if (myNewExpression != null && myNewExpression.isValid() && myNewExpression.getArgumentList() != null) {
        final PsiJavaCodeReferenceElement reference = myNewExpression.getClassOrAnonymousClassReference();
        if (reference != null) {
          final PsiReferenceParameterList parameterList = reference.getParameterList();
          if (parameterList != null) {
            final PsiSubstitutor substitutor = inferTypeArguments();
            final PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
            if (parameters.length != myExpressions.length) return false;
            for (int i = 0, length = parameters.length; i < length; i++) {
              PsiParameter parameter = parameters[i];
              final PsiType expectedType = substitutor.substitute(parameter.getType());
              if (!myExpressions[i].isValid()) return false;
              final PsiType actualType = myExpressions[i].getType();
              if (expectedType == null || actualType == null || !TypeConversionUtil.isAssignable(expectedType, actualType)) return false;
            }
            for (PsiTypeParameter parameter : typeParameters) {
              if (substitutor.substitute(parameter) == null) return false;
            }
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) {
    final PsiTypeParameter[] typeParameters = myPsiClass.getTypeParameters();
    final PsiSubstitutor psiSubstitutor = inferTypeArguments();
    final PsiJavaCodeReferenceElement reference = myNewExpression.getClassOrAnonymousClassReference();
    LOG.assertTrue(reference != null, myNewExpression);
    final PsiReferenceParameterList parameterList = reference.getParameterList();
    LOG.assertTrue(parameterList != null, myNewExpression);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiTypeElement[] elements = parameterList.getTypeParameterElements();
    for (int i = elements.length - 1; i >= 0; i--) {
      PsiType typeArg = Objects.requireNonNull(psiSubstitutor.substitute(typeParameters[i]));
      PsiElement replaced = elements[i].replace(factory.createTypeElement(typeArg));
      JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences(replaced);
    }
  }

  private PsiSubstitutor inferTypeArguments() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myNewExpression.getProject());
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
    final PsiExpressionList argumentList = myNewExpression.getArgumentList();
    LOG.assertTrue(argumentList != null);
    final PsiExpression[] expressions = argumentList.getExpressions();
    return resolveHelper.inferTypeArguments(myPsiClass.getTypeParameters(), parameters, expressions,
                                            PsiSubstitutor.EMPTY,
                                            myNewExpression.getParent(),
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
      final ChangeTypeArgumentsFix fix = new ChangeTypeArgumentsFix(method, psiClass, expressions, context);
      builder.registerFix(fix, null, null, fixRange, null);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
