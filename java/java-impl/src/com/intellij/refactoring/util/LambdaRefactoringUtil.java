/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.util;

import com.intellij.codeInspection.RedundantLambdaCodeBlockInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.util.text.UniqueNameGenerator;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LambdaRefactoringUtil {
  private static final Logger LOG = Logger.getInstance("#" + LambdaRefactoringUtil.class.getName());

  @Nullable
  public static PsiExpression convertToMethodCallInLambdaBody(PsiMethodReferenceExpression element) {
    final PsiLambdaExpression lambdaExpression = convertMethodReferenceToLambda(element, false, true);
    return lambdaExpression != null ? LambdaUtil.extractSingleExpressionFromBody(lambdaExpression.getBody()) : null;
  }

  @Nullable
  public static PsiLambdaExpression convertMethodReferenceToLambda(final PsiMethodReferenceExpression referenceExpression,
                                                                   final boolean ignoreCast, 
                                                                   final boolean simplifyToExpressionLambda) {
    final PsiElement resolve = referenceExpression.resolve();
    final PsiType functionalInterfaceType = referenceExpression.getFunctionalInterfaceType();
    final PsiClassType.ClassResolveResult functionalInterfaceResolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (interfaceMethod == null) {
      return null;
    }
    final PsiSubstitutor psiSubstitutor = LambdaUtil.getSubstitutor(interfaceMethod, functionalInterfaceResolveResult);
    final MethodSignature signature = interfaceMethod.getSignature(psiSubstitutor);
    final boolean isReceiver;
    if (resolve instanceof PsiMethod){
      final PsiMethod method = (PsiMethod)resolve;
      isReceiver = PsiMethodReferenceUtil.isResolvedBySecondSearch(referenceExpression, signature,
                                                                   method.isVarArgs(),
                                                                   method.hasModifierProperty(PsiModifier.STATIC),
                                                                   method.getParameterList().getParametersCount());
    }
    else {
      isReceiver = false;
    }
    final PsiParameter[] psiParameters = resolve instanceof PsiMethod ? ((PsiMethod)resolve).getParameterList().getParameters() : null;

    final StringBuilder buf = new StringBuilder("(");
    LOG.assertTrue(functionalInterfaceType != null);
    buf.append(GenericsUtil.getVariableTypeByExpressionType(functionalInterfaceType).getCanonicalText()).append(")(");
    final PsiParameterList parameterList = interfaceMethod.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();

    final Map<PsiParameter, String> map = new HashMap<>();
    final UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(referenceExpression.getProject());
    final String paramsString = StringUtil.join(parameters, parameter -> {
      final int parameterIndex = parameterList.getParameterIndex(parameter);
      String baseName;
      if (isReceiver && parameterIndex == 0) {
        final SuggestedNameInfo
          nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, psiSubstitutor.substitute(parameter.getType()));
        baseName = nameInfo.names.length > 0 ? nameInfo.names[0] : parameter.getName();
      }
      else {
        final String initialName;
        if (psiParameters != null) {
          final int idx = parameterIndex - (isReceiver ? 1 : 0);
          initialName = psiParameters.length > 0 ? psiParameters[idx < psiParameters.length ? idx : psiParameters.length - 1].getName()
                                                 : parameter.getName();
        }
        else {
          initialName = parameter.getName();
        }
        baseName = codeStyleManager.variableNameToPropertyName(initialName, VariableKind.PARAMETER);
      }

      if (baseName != null) {
        String parameterName = nameGenerator.generateUniqueName(codeStyleManager.suggestUniqueVariableName(baseName, referenceExpression, true));
        map.put(parameter, parameterName);
        return parameterName;
      }
      return "";
    }, ", ");
    buf.append(paramsString);
    buf.append(") -> ");


    final JavaResolveResult resolveResult = referenceExpression.advancedResolve(false);
    final PsiElement resolveElement = resolveResult.getElement();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(referenceExpression.getProject());
    if (resolveElement instanceof PsiMember) {

      buf.append("{");

      if (!PsiType.VOID.equals(interfaceMethod.getReturnType())) {
        buf.append("return ");
      }
      final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(referenceExpression);
      final PsiElement qualifier = referenceExpression.getQualifier();
      PsiClass containingClass = qualifierResolveResult.getContainingClass();

      final boolean onArrayRef =
        elementFactory.getArrayClass(PsiUtil.getLanguageLevel(referenceExpression)) == containingClass;

      final PsiElement referenceNameElement = referenceExpression.getReferenceNameElement();
      if (isReceiver){
        buf.append(map.get(parameters[0])).append(".");
      } else {
        if (!(referenceNameElement instanceof PsiKeyword)) {
          if (qualifier instanceof PsiTypeElement) {
            final PsiJavaCodeReferenceElement referenceElement = ((PsiTypeElement)qualifier).getInnermostComponentReferenceElement();
            LOG.assertTrue(referenceElement != null);
            if (!PsiTreeUtil.isAncestor(containingClass, referenceExpression, false)) {
              buf.append(referenceElement.getReferenceName()).append(".");
            }
          }
          else if (qualifier != null &&

                   !(qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass &&
                     ((PsiReferenceExpression)qualifier).getQualifier() == null && PsiTreeUtil.isAncestor(containingClass, referenceExpression, false) ||

                     qualifier instanceof PsiThisExpression && ((PsiThisExpression)qualifier).getQualifier() == null)) {
            buf.append(qualifier.getText()).append(".");
          }
        }
      }

      //new or method name
      buf.append(referenceExpression.getReferenceName());

      if (referenceNameElement instanceof PsiKeyword) {
        //class name
        buf.append(" ");
        if (onArrayRef) {
          if (qualifier instanceof PsiTypeElement) {
            final PsiType type = ((PsiTypeElement)qualifier).getType();
            int dim = type.getArrayDimensions();
            buf.append(type.getDeepComponentType().getCanonicalText());
            buf.append("[");
            buf.append(map.get(parameters[0]));
            buf.append("]");
            while (--dim > 0) {
              buf.append("[]");
            }
          }
        } else {
          buf.append(((PsiMember)resolveElement).getName());

          final PsiSubstitutor substitutor = resolveResult.getSubstitutor();

          LOG.assertTrue(containingClass != null);
          if (containingClass.hasTypeParameters() && !PsiUtil.isRawSubstitutor(containingClass, substitutor)) {
            buf.append("<").append(StringUtil.join(containingClass.getTypeParameters(), parameter -> {
              final PsiType psiType = substitutor.substitute(parameter);
              LOG.assertTrue(psiType != null);
              return psiType.getCanonicalText();
            }, ", ")).append(">");
          }
        }
      }

      if (!onArrayRef || isReceiver) {
        //param list
        buf.append("(");
        boolean first = true;
        for (int i = isReceiver ? 1 : 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          if (!first) {
            buf.append(", ");
          } else {
            first = false;
          }
          buf.append(map.get(parameter));
        }
        buf.append(")");
      }

      buf.append(";}");
    }


    final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)referenceExpression.replace(elementFactory.createExpressionFromText(buf.toString(), referenceExpression));
    PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)typeCastExpression.getOperand();
    LOG.assertTrue(lambdaExpression != null, buf.toString());
    if (RedundantCastUtil.isCastRedundant(typeCastExpression) || ignoreCast) {
      final PsiExpression operand = typeCastExpression.getOperand();
      LOG.assertTrue(operand != null);
      lambdaExpression = (PsiLambdaExpression)typeCastExpression.replace(operand);
    }

    if (simplifyToExpressionLambda) {
      simplifyToExpressionLambda(lambdaExpression);
    }

    return lambdaExpression;
  }

  public static void simplifyToExpressionLambda(@NotNull final PsiLambdaExpression lambdaExpression) {
    final PsiElement body = lambdaExpression.getBody();
    final PsiExpression singleExpression = RedundantLambdaCodeBlockInspection.isCodeBlockRedundant(body);
    if (singleExpression != null) {
      body.replace(singleExpression);
    }
  }

  /**
   * Works for expression lambdas/one statement code block lambdas to ensures equivalent method ref -> lambda transformation. 
   */
  public static void removeSideEffectsFromLambdaBody(Editor editor, PsiLambdaExpression lambdaExpression) {
    if (lambdaExpression != null && lambdaExpression.isValid()) {
      final PsiElement body = lambdaExpression.getBody();
      PsiExpression methodCall = LambdaUtil.extractSingleExpressionFromBody(body);
      PsiExpression qualifierExpression = null;
      if (methodCall instanceof PsiMethodCallExpression) {
        qualifierExpression = ((PsiMethodCallExpression)methodCall).getMethodExpression().getQualifierExpression();
      }
      else if (methodCall instanceof PsiNewExpression) {
        qualifierExpression = ((PsiNewExpression)methodCall).getQualifier();
      }

      if (qualifierExpression != null) {
        final List<PsiElement> sideEffects = new ArrayList<>();
        SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
        if (!sideEffects.isEmpty()) {
          if (ApplicationManager.getApplication().isUnitTestMode() ||
              Messages.showYesNoDialog(lambdaExpression.getProject(), "There are possible side effects found in method reference qualifier." +
                                                                  "\nIntroduce local variable?", "Side Effects Detected", Messages.getQuestionIcon()) == Messages.YES) {
            //ensure introduced before lambda
            qualifierExpression.putUserData(ElementToWorkOn.PARENT, lambdaExpression);
            new IntroduceVariableHandler().invoke(qualifierExpression.getProject(), editor, qualifierExpression);
          }
        }
      }
    }
  }
}
