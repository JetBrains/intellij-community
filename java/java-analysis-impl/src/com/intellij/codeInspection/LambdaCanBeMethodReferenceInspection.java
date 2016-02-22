/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * User: anna
 */
public class LambdaCanBeMethodReferenceInspection extends BaseJavaBatchLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance("#" + LambdaCanBeMethodReferenceInspection.class.getName());

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Lambda can be replaced with method reference";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Convert2MethodRef";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        if (PsiUtil.getLanguageLevel(expression).isAtLeast(LanguageLevel.JDK_1_8)) {
          final PsiElement body = expression.getBody();
          final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
          if (functionalInterfaceType != null) {
            final PsiCallExpression callExpression = canBeMethodReferenceProblem(body, expression.getParameterList().getParameters(), functionalInterfaceType);
            if (callExpression != null) {
              holder.registerProblem(callExpression,
                                     "Can be replaced with method reference",
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ReplaceWithMethodRefFix());
            }
          }
        }
      }
    };
  }

  @Nullable
  public static PsiCallExpression canBeMethodReferenceProblem(@Nullable final PsiElement body,
                                                              final PsiParameter[] parameters,
                                                              final PsiType functionalInterfaceType) {
    return canBeMethodReferenceProblem(body, parameters, functionalInterfaceType, null);
  }

  @Nullable
  public static PsiCallExpression canBeMethodReferenceProblem(@Nullable final PsiElement body,
                                                              final PsiParameter[] parameters,
                                                              PsiType functionalInterfaceType, 
                                                              @Nullable PsiElement context) {
    final PsiCallExpression callExpression = extractMethodCallFromBlock(body);
    if (callExpression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)callExpression;
      if (newExpression.getAnonymousClass() != null || newExpression.getArrayInitializer() != null) {
        return null;
      }
    }

    final String methodReferenceText = createMethodReferenceText(callExpression, functionalInterfaceType, parameters);
    if (methodReferenceText != null) {
      LOG.assertTrue(callExpression != null);
      final PsiMethod method = callExpression.resolveMethod();
      if (method != null) {
        if (!isSimpleCall(parameters, callExpression, method)) {
          return null;
        }
      }
      else {
        LOG.assertTrue(callExpression instanceof PsiNewExpression);
        if (((PsiNewExpression)callExpression).getQualifier() != null) {
          return null;
        }

        final PsiExpression[] dims = ((PsiNewExpression)callExpression).getArrayDimensions();
        if (dims.length == 1 && parameters.length == 1){
          if (!resolvesToParameter(dims[0], parameters[0])) {
            return null;
          }
        }
        else if (dims.length > 0) {
          return null;
        }
      }
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(callExpression.getProject());
      PsiMethodReferenceExpression methodReferenceExpression = 
        (PsiMethodReferenceExpression)elementFactory.createExpressionFromText(methodReferenceText, context != null ? context : callExpression);
      final Map<PsiElement, PsiType> map = LambdaUtil.getFunctionalTypeMap();
      try {
        map.put(methodReferenceExpression, functionalInterfaceType);
        final JavaResolveResult result = methodReferenceExpression.advancedResolve(false);
        final PsiElement element = result.getElement();
        if (element != null && result.isAccessible() &&
            !(result instanceof MethodCandidateInfo && !((MethodCandidateInfo)result).isApplicable())) {
          if (!(element instanceof PsiMethod)) {
            return callExpression;
          }

          return method != null && MethodSignatureUtil.areSignaturesEqual((PsiMethod)element, method) ? callExpression : null;
        }
      }
      finally {
        map.remove(methodReferenceExpression);
      }
    }
    return null;
  }

  private static boolean isSimpleCall(final PsiParameter[] parameters, PsiCallExpression callExpression, PsiMethod psiMethod) {
    final PsiExpressionList argumentList = callExpression.getArgumentList();
    if (argumentList == null) {
      return false;
    }

    final int calledParametersCount = psiMethod.getParameterList().getParametersCount();
    final PsiExpression[] expressions = argumentList.getExpressions();

    final PsiExpression qualifier;
    if (callExpression instanceof PsiMethodCallExpression) {
      qualifier = ((PsiMethodCallExpression)callExpression).getMethodExpression().getQualifierExpression();
    }
    else if (callExpression instanceof PsiNewExpression) {
      qualifier = ((PsiNewExpression)callExpression).getQualifier();
    }
    else {
      qualifier = null;
    }

    if (expressions.length == 0 && parameters.length == 0) {
      return !(callExpression instanceof PsiNewExpression && qualifier != null);
    }

    final int offset = parameters.length - calledParametersCount;
    if (expressions.length > calledParametersCount || offset < 0) {
      return false;
    }

    for (int i = 0; i < expressions.length; i++) {
      if (!resolvesToParameter(expressions[i], parameters[i + offset])) {
        return false;
      }
    }

    if (offset == 0) {
      if (qualifier != null) {
        final boolean[] parameterUsed = new boolean[] {false};
        qualifier.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitElement(PsiElement element) {
            if (parameterUsed[0]) return;
            super.visitElement(element);
          }

          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            parameterUsed[0] |= ArrayUtil.find(parameters, expression.resolve()) >= 0;
          }
        });
        return !parameterUsed[0];
      }
      return true;
    }

    return resolvesToParameter(qualifier, parameters[0]);
  }

  private static boolean resolvesToParameter(PsiExpression expression, PsiParameter parameter) {
    return expression instanceof PsiReferenceExpression && ((PsiReferenceExpression)expression).resolve() == parameter;
  }

  public static PsiCallExpression extractMethodCallFromBlock(PsiElement body) {
    final PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
    if (expression instanceof PsiNewExpression) {
      if (checkQualifier(((PsiNewExpression)expression).getQualifier())) {
        return (PsiCallExpression)expression;
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      if (checkQualifier(((PsiMethodCallExpression)expression).getMethodExpression().getQualifier())) {
        return (PsiCallExpression)expression;
      }
    }
    return null;
  }

  private static boolean checkQualifier(PsiElement qualifier) {
    if (qualifier == null) {
      return true;
    }
    final Condition<PsiElement> callExpressionCondition = Conditions.instanceOf(PsiCallExpression.class);
    final Condition<PsiElement> nonFinalFieldRefCondition = new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement expression) {
        if (expression instanceof PsiReferenceExpression) {
          PsiElement element = ((PsiReferenceExpression)expression).resolve();
          if (element instanceof PsiField && !((PsiField)element).hasModifierProperty(PsiModifier.FINAL)) {
            return true;
          }
        }
        return false;
      }
    };
    return SyntaxTraverser
      .psiTraverser()
      .withRoot(qualifier)
      .filter(Conditions.or(callExpressionCondition, nonFinalFieldRefCondition)).toList().isEmpty();
  }

  @Nullable
  private static PsiMethod getNonAmbiguousReceiver(PsiParameter[] parameters, @NotNull PsiMethod psiMethod) {
    String methodName = psiMethod.getName();
    PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return null;

    final PsiMethod[] psiMethods = containingClass.findMethodsByName(methodName, false);
    if (psiMethods.length == 1) return psiMethod;

    final PsiType receiverType = parameters[0].getType();
    for (PsiMethod method : psiMethods) {
      if (isPairedNoReceiver(parameters, receiverType, method)) {
        final PsiMethod[] deepestSuperMethods = psiMethod.findDeepestSuperMethods();
        if (deepestSuperMethods.length > 0) {
          for (PsiMethod superMethod : deepestSuperMethods) {
            PsiMethod validSuperMethod = getNonAmbiguousReceiver(parameters, superMethod);
            if (validSuperMethod != null) return validSuperMethod;
          }
        }
        return null;
      }
    }
    return psiMethod;
  }

  private static boolean isPairedNoReceiver(PsiParameter[] parameters,
                                            PsiType receiverType,
                                            PsiMethod method) {
    final PsiParameter[] nonReceiverCandidateParams = method.getParameterList().getParameters();
    return nonReceiverCandidateParams.length == parameters.length &&
           method.hasModifierProperty(PsiModifier.STATIC) &&
           TypeConversionUtil.areTypesConvertible(nonReceiverCandidateParams[0].getType(), receiverType);
  }

  @Nullable
  public static String createMethodReferenceText(final PsiElement element,
                                                 final PsiType functionalInterfaceType,
                                                 final PsiParameter[] parameters) {
    if (element instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;

      JavaResolveResult result = methodCall.resolveMethodGenerics();
      final PsiMethod psiMethod = (PsiMethod)result.getElement();
      if (psiMethod == null) {
        return null;
      }

      final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      final String qualifierByMethodCall = getQualifierTextByMethodCall(methodCall, functionalInterfaceType, parameters, psiMethod, result.getSubstitutor());
      if (qualifierByMethodCall != null) {
        return qualifierByMethodCall + "::" + ((PsiMethodCallExpression)element).getTypeArgumentList().getText() + methodExpression.getReferenceName();
      }
    }
    else if (element instanceof PsiNewExpression) {
      final String qualifierByNew = getQualifierTextByNewExpression((PsiNewExpression)element);
      if (qualifierByNew != null) {
        return qualifierByNew + ((PsiNewExpression)element).getTypeArgumentList().getText() + "::new";
      }
    }
    return null;
  }

  private static String getQualifierTextByNewExpression(PsiNewExpression element) {
    final PsiType newExprType = element.getType();
    if (newExprType == null) {
      return null;
    }

    PsiClass containingClass = null;
    final PsiJavaCodeReferenceElement classReference = element.getClassOrAnonymousClassReference();
    if (classReference != null) {
      final JavaResolveResult resolve = classReference.advancedResolve(false);
      final PsiElement resolveElement = resolve.getElement();
      if (resolveElement instanceof PsiClass) {
        containingClass = (PsiClass)resolveElement;
      }
    }

    String classOrPrimitiveName = null;
    if (containingClass != null) {
      classOrPrimitiveName = getClassReferenceName(containingClass);
    } 
    else if (newExprType instanceof PsiArrayType){
      final PsiType deepComponentType = newExprType.getDeepComponentType();
      if (deepComponentType instanceof PsiPrimitiveType) {
        classOrPrimitiveName = deepComponentType.getCanonicalText();
      }
    }

    if (classOrPrimitiveName == null) {
      return null;
    }

    int dim = newExprType.getArrayDimensions();
    while (dim-- > 0) {
      classOrPrimitiveName += "[]";
    }
    return classOrPrimitiveName;
  }

  @Nullable
  private static String getQualifierTextByMethodCall(final PsiMethodCallExpression methodCall,
                                                     final PsiType functionalInterfaceType,
                                                     final PsiParameter[] parameters,
                                                     final PsiMethod psiMethod, 
                                                     final PsiSubstitutor substitutor) {

    final PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();

    final PsiClass containingClass = psiMethod.getContainingClass();
    LOG.assertTrue(containingClass != null);

    if (qualifierExpression != null) {
      boolean isReceiverType = false;
      if (qualifierExpression instanceof PsiReferenceExpression && ArrayUtil.find(parameters, ((PsiReferenceExpression)qualifierExpression).resolve()) > -1) {
        isReceiverType = PsiMethodReferenceUtil.isReceiverType(PsiMethodReferenceUtil.getFirstParameterType(functionalInterfaceType, qualifierExpression), containingClass, substitutor);
      }
      return isReceiverType ? composeReceiverQualifierText(parameters, psiMethod, containingClass, qualifierExpression)
                            : qualifierExpression.getText();
    }
    else {
      if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return getClassReferenceName(containingClass);
      }
      else {
        PsiClass parentContainingClass = PsiTreeUtil.getParentOfType(methodCall, PsiClass.class);
        if (parentContainingClass instanceof PsiAnonymousClass) {
          parentContainingClass = PsiTreeUtil.getParentOfType(parentContainingClass, PsiClass.class, true);
        }
        PsiClass treeContainingClass = parentContainingClass;
        while (treeContainingClass != null && !InheritanceUtil.isInheritorOrSelf(treeContainingClass, containingClass, true)) {
          treeContainingClass = PsiTreeUtil.getParentOfType(treeContainingClass, PsiClass.class, true);
        }
        if (treeContainingClass != null && containingClass != parentContainingClass && treeContainingClass != parentContainingClass) {
          final String treeContainingClassName = treeContainingClass.getName();
          if (treeContainingClassName == null) {
            return null;
          }
          return treeContainingClassName + ".this";
        }
        else {
          return "this";
        }
      }
    }
  }

  @Nullable
  private static String composeReceiverQualifierText(PsiParameter[] parameters,
                                                     PsiMethod psiMethod,
                                                     PsiClass containingClass,
                                                     @NotNull PsiExpression qualifierExpression) {
    if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return null;
    }

    final PsiMethod nonAmbiguousMethod = getNonAmbiguousReceiver(parameters, psiMethod);
    if (nonAmbiguousMethod == null) {
      return null;
    }

    final PsiClass nonAmbiguousContainingClass = nonAmbiguousMethod.getContainingClass();
    if (!containingClass.equals(nonAmbiguousContainingClass)) {
      return getClassReferenceName(nonAmbiguousContainingClass);
    }

    if (containingClass.isPhysical() && qualifierExpression instanceof PsiReferenceExpression && !PsiTypesUtil.isGetClass(psiMethod)) {
      final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
      final boolean parameterWithoutFormalType = resolve instanceof PsiParameter && ((PsiParameter)resolve).getTypeElement() == null;
      if (parameterWithoutFormalType && ArrayUtil.find(parameters, resolve) > -1) {
        return getClassReferenceName(containingClass);
      }
    }

    final PsiType qualifierExpressionType = qualifierExpression.getType();
    if (qualifierExpressionType != null && !FunctionalInterfaceParameterizationUtil.isWildcardParameterized(qualifierExpressionType)) {
      try {
        final String canonicalText = qualifierExpressionType.getCanonicalText();
        JavaPsiFacade.getElementFactory(containingClass.getProject()).createExpressionFromText(canonicalText + "::foo", qualifierExpression);
        return canonicalText;
      }
      catch (IncorrectOperationException ignore){}
    }
    return getClassReferenceName(containingClass);
  }

  private static String getClassReferenceName(PsiClass containingClass) {
    final String qualifiedName = containingClass.getQualifiedName();
    if (qualifiedName != null) {
      return qualifiedName;
    }
    else {
      return containingClass.getName();
    }
  }

  private static class ReplaceWithMethodRefFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return "Replace lambda with method reference";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (lambdaExpression == null) return;
      PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
      if (functionalInterfaceType == null || !functionalInterfaceType.isValid()) return;
      final PsiType denotableFunctionalInterfaceType = RefactoringChangeUtil.getTypeByExpression(lambdaExpression);
      if (denotableFunctionalInterfaceType == null) return;
      final String methodRefText = createMethodReferenceText(element, functionalInterfaceType,
                                                             lambdaExpression.getParameterList().getParameters());

      if (methodRefText != null) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final PsiExpression psiExpression = factory.createExpressionFromText(methodRefText, lambdaExpression);
        final SmartTypePointer typePointer = SmartTypePointerManager.getInstance(project).createSmartTypePointer(denotableFunctionalInterfaceType);
        PsiElement replace = lambdaExpression.replace(psiExpression);
        final PsiType functionalTypeAfterReplacement = ((PsiMethodReferenceExpression)replace).getFunctionalInterfaceType();
        functionalInterfaceType = typePointer.getType();
        if (functionalTypeAfterReplacement == null || functionalInterfaceType != null && !functionalTypeAfterReplacement.equals(functionalInterfaceType)) { //ambiguity
          final PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(A)a", replace);
          cast.getCastType().replace(factory.createTypeElement(functionalInterfaceType));
          cast.getOperand().replace(replace);
          replace = replace.replace(cast);
        }
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(replace);
      }
    }
  }
}
