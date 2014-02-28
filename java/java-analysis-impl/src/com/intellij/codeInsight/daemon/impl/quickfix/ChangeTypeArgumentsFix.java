/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author cdr
 * @since Nov 13, 2002
 */
public class ChangeTypeArgumentsFix implements IntentionAction, HighPriorityAction {
  private static final Logger LOG = Logger.getInstance("#" + ChangeTypeArgumentsFix.class.getName());

  private final PsiMethod myTargetMethod;
  private final PsiClass myPsiClass;
  private final PsiExpression[] myExpressions;
  private final PsiNewExpression myNewExpression;

  ChangeTypeArgumentsFix(@NotNull PsiMethod targetMethod,
                         PsiClass psiClass,
                         @NotNull PsiExpression[] expressions,
                         @NotNull PsiElement context) {
    myTargetMethod = targetMethod;
    myPsiClass = psiClass;
    myExpressions = expressions;
    myNewExpression = PsiTreeUtil.getParentOfType(context, PsiNewExpression.class);
  }

  @Override
  @NotNull
  public String getText() {
    final PsiSubstitutor substitutor = inferTypeArguments();
    return "Change type arguments to <" + StringUtil.join(myPsiClass.getTypeParameters(), new Function<PsiTypeParameter, String>() {
      @Override
      public String fun(PsiTypeParameter typeParameter) {
        final PsiType substituted = substitutor.substitute(typeParameter);
        return substituted != null ? substituted.getPresentableText() : CommonClassNames.JAVA_LANG_OBJECT;
      }
    }, ", ") + ">";
  }


  @Override
  @NotNull
  public String getFamilyName() {
    return "Change type arguments";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
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
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    final PsiTypeParameter[] typeParameters = myPsiClass.getTypeParameters();
    final PsiSubstitutor psiSubstitutor = inferTypeArguments();
    final PsiJavaCodeReferenceElement reference = myNewExpression.getClassOrAnonymousClassReference();
    LOG.assertTrue(reference != null, myNewExpression);
    final PsiReferenceParameterList parameterList = reference.getParameterList();
    LOG.assertTrue(parameterList != null, myNewExpression);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiTypeElement[] elements = parameterList.getTypeParameterElements();
    for (int i = elements.length - 1; i >= 0; i--) {
      PsiType typeArg = assertNotNull(psiSubstitutor.substitute(typeParameters[i]));
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


  public static void registerIntentions(@NotNull JavaResolveResult[] candidates,
                                        @NotNull PsiExpressionList list,
                                        @Nullable HighlightInfo highlightInfo,
                                        PsiClass psiClass) {
    if (candidates.length == 0) return;
    PsiExpression[] expressions = list.getExpressions();
    for (JavaResolveResult candidate : candidates) {
      registerIntention(expressions, highlightInfo, psiClass, candidate, list);
    }
  }

  private static void registerIntention(@NotNull PsiExpression[] expressions,
                                        @Nullable HighlightInfo highlightInfo,
                                        PsiClass psiClass,
                                        @NotNull JavaResolveResult candidate,
                                        @NotNull PsiElement context) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    if (method != null && context.getManager().isInProject(method)) {
      final ChangeTypeArgumentsFix fix = new ChangeTypeArgumentsFix(method, psiClass, expressions, context);
      QuickFixAction.registerQuickFixAction(highlightInfo, null, fix);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
