// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class PsiDiamondTypeUtil {
  private static final Logger LOG = Logger.getInstance(PsiDiamondTypeUtil.class);

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
              PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
              if (anonymousClass != null &&
                  ContainerUtil.exists(anonymousClass.getMethods(), 
                                       method -> !method.hasModifierProperty(PsiModifier.PRIVATE) && method.findSuperMethods().length == 0)) {
                return false;
              }
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
                  return areTypeArgumentsRedundant(typeArguments, context, true, method, typeParameters);
                }
              }
            }
          }
        }
      }
    }
    return false;
  }

  public static PsiElement createExplicitReplacement(PsiElement psiElement) {
    if (psiElement instanceof PsiReferenceParameterList) {
      final PsiNewExpression expression =
        (PsiNewExpression)JavaPsiFacade.getElementFactory(psiElement.getProject()).createExpressionFromText("new a<>()", psiElement);
      final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      LOG.assertTrue(classReference != null);
      final PsiReferenceParameterList parameterList = classReference.getParameterList();
      LOG.assertTrue(parameterList != null);
      return parameterList;
    }
    return null;
  }

  public static PsiElement replaceDiamondWithExplicitTypes(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) {
      return parent;
    }
    final PsiJavaCodeReferenceElement javaCodeReferenceElement = (PsiJavaCodeReferenceElement) parent;
    PsiReferenceParameterList parameterList = javaCodeReferenceElement.getParameterList();
    if (parameterList == null) return javaCodeReferenceElement;

    final StringBuilder text = new StringBuilder();
    text.append(javaCodeReferenceElement.getQualifiedName());
    text.append('<');
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    final PsiDiamondType.DiamondInferenceResult result = PsiDiamondTypeImpl.resolveInferredTypesNoCheck(newExpression, newExpression);
    text.append(StringUtil.join(result.getInferredTypes(), psiType -> psiType.getCanonicalText(), ","));
    text.append('>');
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiJavaCodeReferenceElement newReference = elementFactory.createReferenceFromText(text.toString(), element);
    PsiReferenceParameterList newReferenceParameterList = newReference.getParameterList();
    LOG.assertTrue(newReferenceParameterList != null);
    CodeStyleManager.getInstance(javaCodeReferenceElement.getProject()).reformat(parameterList.replace(newReferenceParameterList));
    return javaCodeReferenceElement;
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

  private static boolean isAugmented(PsiExpression expression) {
    PsiElement gParent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    PsiTypeElement typeElement = null;
    if (gParent instanceof PsiVariable) {
      typeElement = ((PsiVariable)gParent).getTypeElement();
    }
    else if (gParent instanceof PsiReturnStatement) {
      PsiElement method = PsiTreeUtil.getParentOfType(gParent, PsiMethod.class, PsiLambdaExpression.class);
      typeElement = method instanceof PsiMethod ? ((PsiMethod)method).getReturnTypeElement() : null;
    }
    return typeElement != null && PsiAugmentProvider.getInferredType(typeElement) != null;
  }

  public static boolean areTypeArgumentsRedundant(PsiType[] typeArguments,
                                                  PsiExpression context,
                                                  boolean constructorRef,
                                                  @Nullable PsiMethod method,
                                                  PsiTypeParameter[] typeParameters) {
    PsiElement encoded = null;
    try {
      final PsiElement copy;
      final PsiType typeByParent = PsiTypesUtil.getExpectedTypeByParent(context);
      if (typeByParent != null && PsiTypesUtil.isDenotableType(typeByParent, context)) {
        if (isAugmented(context)) {
          return false;
        }
        RecaptureTypeMapper.encode(encoded = context);
        copy = LambdaUtil.copyWithExpectedType(context, typeByParent);
      }
      else {
        final PsiExpressionList argumentList = context instanceof PsiCallExpression ? ((PsiCallExpression)context).getArgumentList() : null;
        final Object marker = new Object();
        PsiTreeUtil.mark(argumentList != null ? argumentList : context, marker);
        final PsiCall call = LambdaUtil.treeWalkUp(context);
        if (call != null) {
          RecaptureTypeMapper.encode(encoded = call);
          final PsiCall callCopy = LambdaUtil.copyTopLevelCall(call);
          copy = callCopy != null ? PsiTreeUtil.releaseMark(callCopy, marker) : null;
        }
        else  {
          final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(context.getProject());
          if (injectedLanguageManager.getInjectionHost(context) != null) {
            return false;
          }
          final PsiFile containingFile = context.getContainingFile();
          final PsiFile fileCopy = (PsiFile)containingFile.copy();
          copy = PsiTreeUtil.releaseMark(fileCopy, marker);
          if (method != null && method.getContainingFile() == containingFile) {
            final PsiElement startMethodElementInCopy = fileCopy.findElementAt(method.getTextOffset());
            method = PsiTreeUtil.getParentOfType(startMethodElementInCopy, PsiMethod.class);
            if (method == null) {
              //lombok generated builder
              return false;
            }
          }
        }
      }
      final PsiCallExpression exprCopy = PsiTreeUtil.getParentOfType(copy, PsiCallExpression.class, false);
      if (context instanceof PsiMethodReferenceExpression) {
        PsiMethodReferenceExpression methodRefCopy = PsiTreeUtil.getParentOfType(copy, PsiMethodReferenceExpression.class, false);
        if (methodRefCopy != null && !isInferenceEquivalent(typeArguments, typeParameters, method, methodRefCopy)) {
          return false;
        }
      }
      else if (exprCopy != null) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(exprCopy.getProject());
        if (constructorRef) {
          if (!(exprCopy instanceof PsiNewExpression) ||
              !isInferenceEquivalent(typeArguments, elementFactory, (PsiNewExpression)exprCopy)) {
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

      if (typeByParent != null) {
        return true;
      }

      PsiCallExpression newParentCall = exprCopy != null ? PsiTreeUtil.getParentOfType(exprCopy, PsiCallExpression.class) : null;
      PsiCallExpression oldParentCall = PsiTreeUtil.getParentOfType(context, PsiCallExpression.class);
      if (newParentCall != null && oldParentCall != null) {
        JavaResolveResult newResult = newParentCall.resolveMethodGenerics();
        JavaResolveResult oldResult = oldParentCall.resolveMethodGenerics();
        if (newResult.getElement() == null ||
            !newResult.getElement().isEquivalentTo(oldResult.getElement()) ||
            !new RecaptureTypeMapper().recapture(newResult.getSubstitutor()).equals(oldResult.getSubstitutor())) {
          return false;
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.info(e);
      return false;
    }
    finally {
      if (encoded != null) {
        RecaptureTypeMapper.clean(encoded);
      }
    }
    return true;
  }

  private static boolean isInferenceEquivalent(PsiType[] typeArguments,
                                               PsiTypeParameter[] typeParameters,
                                               PsiMethod method,
                                               PsiMethodReferenceExpression methodRefCopy) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(methodRefCopy.getProject());
    PsiTypeElement qualifierType = methodRefCopy.getQualifierType();
    if (qualifierType != null) {
      qualifierType.replace(elementFactory.createTypeElement(((PsiClassType)qualifierType.getType()).rawType()));
    }
    else {
      PsiReferenceParameterList parameterList = methodRefCopy.getParameterList();
      if (parameterList != null) {
        parameterList.delete();
      }
    }

    JavaResolveResult result = methodRefCopy.advancedResolve(false);
    if (method != null && result.getElement() != method) return false;

    final PsiSubstitutor psiSubstitutor = result.getSubstitutor();
    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      final PsiType inferredType = psiSubstitutor.getSubstitutionMap().get(typeParameter);
      if (!typeArguments[i].equals(inferredType)) {
        return false;
      }
    }
    return checkParentApplicability(methodRefCopy);
  }

  private static boolean isInferenceEquivalent(PsiType[] typeArguments,
                                               PsiElementFactory elementFactory,
                                               PsiCallExpression exprCopy,
                                               PsiMethod method,
                                               PsiTypeParameter[] typeParameters) throws IncorrectOperationException {
    PsiReferenceParameterList list = ((PsiCallExpression)elementFactory.createExpressionFromText("foo()", null)).getTypeArgumentList();
    exprCopy.getTypeArgumentList().replace(list);

    final JavaResolveResult copyResult = exprCopy.resolveMethodGenerics();
    if (!method.isEquivalentTo(copyResult.getElement())) return false;
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

    return checkParentApplicability(exprCopy);
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

    return checkParentApplicability(exprCopy);
  }

  private static boolean checkParentApplicability(PsiExpression exprCopy) {
    while (exprCopy != null){
      JavaResolveResult resolveResult = exprCopy instanceof PsiCallExpression ? PsiDiamondType.getDiamondsAwareResolveResult((PsiCall)exprCopy) : null;
      if (resolveResult instanceof MethodCandidateInfo && !((MethodCandidateInfo)resolveResult).isApplicable()) {
        return false;
      }
      exprCopy = PsiTreeUtil.getParentOfType(exprCopy, PsiCallExpression.class, true);
    }
    return true;
  }
}
