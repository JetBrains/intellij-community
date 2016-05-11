/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: anna
 */
public class PsiDiamondTypeUtil {
  private static final Logger LOG = Logger.getInstance("#" + PsiDiamondTypeUtil.class.getName());

  private PsiDiamondTypeUtil() {
  }

  public static boolean canCollapseToDiamond(final PsiNewExpression expression,
                                             final PsiNewExpression context,
                                             @Nullable final PsiType expectedType) {
    return canCollapseToDiamond(expression, context, expectedType, false);
  }

  public static boolean canChangeContextForDiamond(final PsiNewExpression expression, final PsiType expectedType) {
    final PsiNewExpression copy = (PsiNewExpression)expression.copy();
    return canCollapseToDiamond(copy, copy, expectedType, true);
  }

  private static boolean canCollapseToDiamond(final PsiNewExpression expression,
                                             final PsiNewExpression context,
                                             @Nullable final PsiType expectedType,
                                             boolean skipDiamonds) {
    if (PsiUtil.getLanguageLevel(context).isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
      if (classReference != null) {
        final PsiReferenceParameterList parameterList = classReference.getParameterList();
        if (parameterList != null) {
          final PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();
          if (typeElements.length > 0) {
            if (!skipDiamonds && typeElements.length == 1 && typeElements[0].getType() instanceof PsiDiamondType) return false;
            final PsiDiamondTypeImpl.DiamondInferenceResult inferenceResult = PsiDiamondTypeImpl.resolveInferredTypes(expression, context);
            if (inferenceResult.getErrorMessage() == null) {
              final List<PsiType> types = inferenceResult.getInferredTypes();
              PsiType[] typeArguments = null;
              if (expectedType instanceof PsiClassType) {
                typeArguments = ((PsiClassType)expectedType).getParameters();
              }
              if (typeArguments == null) {
                typeArguments = parameterList.getTypeArguments();
              }
              if (types.size() == typeArguments.length) {
                final PsiMethod method = expression.resolveMethod();
                final PsiElement resolve = classReference.resolve();
                if (resolve instanceof PsiClass) {
                  final PsiTypeParameter[] typeParameters = ((PsiClass)resolve).getTypeParameters();
                  return areTypeArgumentsRedundant(typeArguments, expression, true, method, typeParameters);
                }
              }
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  public static PsiElement replaceExplicitWithDiamond(PsiElement psiElement) {
    if (psiElement instanceof PsiReferenceParameterList) {
      if (!FileModificationService.getInstance().prepareFileForWrite(psiElement.getContainingFile())) return psiElement;
      final PsiNewExpression expression =
        (PsiNewExpression)JavaPsiFacade.getElementFactory(psiElement.getProject()).createExpressionFromText("new a<>()", psiElement);
      final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      LOG.assertTrue(classReference != null);
      final PsiReferenceParameterList parameterList = classReference.getParameterList();
      LOG.assertTrue(parameterList != null);
      return psiElement.replace(parameterList);
    }
    return psiElement;
  }

  public static PsiElement replaceDiamondWithExplicitTypes(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) {
      return parent;
    }
    final PsiJavaCodeReferenceElement javaCodeReferenceElement = (PsiJavaCodeReferenceElement) parent;
    final StringBuilder text = new StringBuilder();
    text.append(javaCodeReferenceElement.getQualifiedName());
    text.append('<');
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    final PsiDiamondType.DiamondInferenceResult result = PsiDiamondTypeImpl.resolveInferredTypesNoCheck(newExpression, newExpression);
    text.append(StringUtil.join(result.getInferredTypes(), new Function<PsiType, String>() {
      @Override
      public String fun(PsiType psiType) {
        return psiType.getCanonicalText();
      }
    }, ","));
    text.append('>');
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiJavaCodeReferenceElement newReference = elementFactory.createReferenceFromText(text.toString(), element);
    return CodeStyleManager.getInstance(javaCodeReferenceElement.getProject()).reformat(javaCodeReferenceElement.replace(newReference));
  }

  public static PsiExpression expandTopLevelDiamondsInside(PsiExpression expr) {
    if (expr instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)expr).getClassReference();
      if (classReference != null) {
        final PsiReferenceParameterList parameterList = classReference.getParameterList();
        if (parameterList != null) {
          final PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
          if (typeParameterElements.length == 1 && typeParameterElements[0].getType() instanceof PsiDiamondType) {
            return  (PsiExpression)replaceDiamondWithExplicitTypes(parameterList).getParent();
          }
        }
      }
    }
    return expr;
  }

  public static String getCollapsedType(PsiType type, PsiElement context) {
    String typeText = type.getCanonicalText();
    if (PsiUtil.isLanguageLevel7OrHigher(context)) {
      final int idx = typeText.indexOf('<');
      if (idx >= 0) {
        return typeText.substring(0, idx) + "<>";
      }
    }
    return typeText;
  }

  public static boolean areTypeArgumentsRedundant(PsiType[] typeArguments,
                                                  PsiCallExpression expression,
                                                  boolean constructorRef,
                                                  @Nullable PsiMethod method, 
                                                  PsiTypeParameter[] typeParameters) {
    try {
      final PsiElement copy;
      final PsiType typeByParent = PsiTypesUtil.getExpectedTypeByParent(expression);
      if (typeByParent != null) {
        final String arrayInitializer = "new " + typeByParent.getCanonicalText() + "[]{0}";
        final PsiNewExpression newExpr =
          (PsiNewExpression)JavaPsiFacade.getInstance(expression.getProject()).getElementFactory().createExpressionFromText(arrayInitializer, expression);
        final PsiArrayInitializerExpression initializer = newExpr.getArrayInitializer();
        LOG.assertTrue(initializer != null);
        copy = initializer.getInitializers()[0].replace(expression);
      }
      else {
        final PsiExpressionList argumentList = expression.getArgumentList();
        final int offset = (argumentList != null ? argumentList : expression).getTextRange().getStartOffset();
        final PsiCall call = LambdaUtil.treeWalkUp(expression);
        if (call != null) {
          final PsiCall callCopy = LambdaUtil.copyTopLevelCall(call);
          copy = callCopy != null ? callCopy.findElementAt(offset - call.getTextRange().getStartOffset()) : null;
        }
        else  {
          final PsiFile containingFile = expression.getContainingFile();
          final PsiFile fileCopy = (PsiFile)containingFile.copy();
          copy = fileCopy.findElementAt(offset);
          if (method != null && method.getContainingFile() == containingFile) {
            final PsiElement startMethodElementInCopy = fileCopy.findElementAt(method.getTextOffset());
            method = PsiTreeUtil.getParentOfType(startMethodElementInCopy, PsiMethod.class);
            LOG.assertTrue(method != null, startMethodElementInCopy);
          }
        }
      }
      final PsiCallExpression exprCopy = PsiTreeUtil.getParentOfType(copy, PsiCallExpression.class, false);
      if (exprCopy != null) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(exprCopy.getProject()).getElementFactory();
        if (constructorRef) {
          if (!(exprCopy instanceof PsiNewExpression) || !isInferenceEquivalent(typeArguments, elementFactory, (PsiNewExpression)exprCopy)) {
            return false;
          }
        }
        else {
          LOG.assertTrue(method != null);
          if (!isInferenceEquivalent(typeArguments, elementFactory, exprCopy, method, typeParameters)) {
            return false;
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.info(e);
      return false;
    }
    return true;
  }

  private static boolean isInferenceEquivalent(PsiType[] typeArguments,
                                               PsiElementFactory elementFactory,
                                               PsiCallExpression exprCopy,
                                               PsiMethod method, 
                                               PsiTypeParameter[] typeParameters) throws IncorrectOperationException {
    PsiReferenceParameterList list = ((PsiCallExpression)elementFactory.createExpressionFromText("foo()", null)).getTypeArgumentList();
    exprCopy.getTypeArgumentList().replace(list);

    final JavaResolveResult copyResult = exprCopy.resolveMethodGenerics();
    if (method != copyResult.getElement()) return false;
    final PsiSubstitutor psiSubstitutor = copyResult.getSubstitutor();
    for (int i = 0, length = typeParameters.length; i < length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      final PsiType inferredType = psiSubstitutor.getSubstitutionMap().get(typeParameter);
      if (!typeArguments[i].equals(inferredType)) {
        return false;
      }
      if (PsiUtil.resolveClassInType(method.getReturnType()) == typeParameter && PsiPrimitiveType.getUnboxedType(inferredType) != null) {
        return false;
      }
    }
    return true;
  }

  private static boolean isInferenceEquivalent(PsiType[] typeArguments, 
                                               PsiElementFactory elementFactory, 
                                               PsiNewExpression exprCopy) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement collapsedClassReference = ((PsiNewExpression)elementFactory.createExpressionFromText("new A<>()", null)).getClassOrAnonymousClassReference();
    LOG.assertTrue(collapsedClassReference != null);
    final PsiReferenceParameterList diamondParameterList = collapsedClassReference.getParameterList();
    LOG.assertTrue(diamondParameterList != null);

    final PsiJavaCodeReferenceElement classReference = exprCopy.getClassOrAnonymousClassReference();
    LOG.assertTrue(classReference != null);
    final PsiReferenceParameterList parameterList = classReference.getParameterList();
    LOG.assertTrue(parameterList != null);
    parameterList.replace(diamondParameterList);

    final PsiType[] inferredArgs = classReference.getParameterList().getTypeArguments();
    if (typeArguments.length != inferredArgs.length) {
      return false;
    }

    for (int i = 0; i < typeArguments.length; i++) {
      PsiType typeArgument = typeArguments[i];
      if (inferredArgs[i] instanceof PsiWildcardType) {
        final PsiWildcardType wildcardType = (PsiWildcardType)inferredArgs[i];
        final PsiType bound = wildcardType.getBound();
        if (bound != null) {
          if (wildcardType.isExtends()) {
            if (bound.isAssignableFrom(typeArgument)) continue;
          }
          else {
            if (typeArgument.isAssignableFrom(bound)) continue;
          }
        }
      }
      if (!typeArgument.equals(inferredArgs[i])) {
        return false;
      }
    }
    return true;
  }
}
