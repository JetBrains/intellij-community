// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.codeInspection.RedundantLambdaCodeBlockInspection;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.RecaptureTypeMapper;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.UniqueNameGenerator;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class LambdaRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(LambdaRefactoringUtil.class);

  public static @Nullable PsiExpression convertToMethodCallInLambdaBody(PsiMethodReferenceExpression element) {
    final PsiLambdaExpression lambdaExpression = convertMethodReferenceToLambda(element, false, true);
    return lambdaExpression != null ? LambdaUtil.extractSingleExpressionFromBody(lambdaExpression.getBody()) : null;
  }

  public static @Nullable PsiLambdaExpression convertMethodReferenceToLambda(final PsiMethodReferenceExpression referenceExpression,
                                                                             final boolean ignoreCast,
                                                                             final boolean simplifyToExpressionLambda) {
    PsiLambdaExpression lambdaExpression = createLambda(referenceExpression, ignoreCast);
    if (lambdaExpression == null) return null;
    lambdaExpression = (PsiLambdaExpression)new CommentTracker().replaceAndRestoreComments(referenceExpression, lambdaExpression);

    if (simplifyToExpressionLambda) {
      simplifyToExpressionLambda(lambdaExpression);
    }

    return lambdaExpression;
  }

  public static boolean canConvertToLambda(PsiMethodReferenceExpression referenceExpression) {
    return createLambda(referenceExpression, false) != null;
  }

  /**
   * Convert method reference to lambda if possible and return the created lambda without replacing original method reference.
   *
   * @param referenceExpression a method reference to convert
   * @param doNotAddParameterTypes if false, parameter types could be added to the lambda to resolve ambiguity
   * @return a created lambda or null if conversion fails
   */
  public static PsiLambdaExpression createLambda(PsiMethodReferenceExpression referenceExpression, boolean doNotAddParameterTypes) {
    String lambda = createLambdaWithoutFormalParameters(referenceExpression);
    if (lambda == null) return null;

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(referenceExpression.getProject());
    PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)elementFactory.createExpressionFromText(lambda, referenceExpression);
    final PsiType functionalInterfaceType = referenceExpression.getFunctionalInterfaceType();
    boolean needToSpecifyFormalTypes = !doNotAddParameterTypes && !isInferredSameTypeAfterConversion(lambdaExpression, referenceExpression);
    if (needToSpecifyFormalTypes) {
      PsiParameterList typedParamList = LambdaUtil.specifyLambdaParameterTypes(functionalInterfaceType, lambdaExpression);
      if (typedParamList == null) {
        return null;
      }
    }
    return lambdaExpression;
  }

  private static String createLambdaWithoutFormalParameters(PsiMethodReferenceExpression referenceExpression) {
    PsiType functionalInterfaceType = referenceExpression.getFunctionalInterfaceType();
    final PsiElement resolve = referenceExpression.resolve();
    if (resolve == null) {
      return null;
    }
    final PsiClassType.ClassResolveResult functionalInterfaceResolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (interfaceMethod == null) {
      return null;
    }
    final PsiSubstitutor psiSubstitutor = LambdaUtil.getSubstitutor(interfaceMethod, functionalInterfaceResolveResult);
    final MethodSignature signature = interfaceMethod.getSignature(psiSubstitutor);
    final boolean isReceiver;
    if (resolve instanceof PsiMethod method){
      isReceiver = PsiMethodReferenceUtil.isResolvedBySecondSearch(referenceExpression, signature,
                                                                   method.isVarArgs(),
                                                                   method.hasModifierProperty(PsiModifier.STATIC),
                                                                   method.getParameterList().getParametersCount());
    }
    else {
      isReceiver = false;
    }
    final PsiParameter[] psiParameters = resolve instanceof PsiMethod ? ((PsiMethod)resolve).getParameterList().getParameters() : null;

    final PsiParameterList parameterList = interfaceMethod.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();

    final Map<PsiParameter, String> map = new HashMap<>();
    final UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(referenceExpression.getProject());
    Function<PsiParameter, String> paramPresentationFunction = parameter -> {
      final int parameterIndex = parameterList.getParameterIndex(parameter);
      String baseName;
      if (isReceiver && parameterIndex == 0) {
        final SuggestedNameInfo
          nameInfo =
          codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, psiSubstitutor.substitute(parameter.getType()));
        baseName = nameInfo.names.length > 0 ? nameInfo.names[0] : parameter.getName();
      }
      else {
        String initialName;
        if (psiParameters != null) {
          final int idx = parameterIndex - (isReceiver ? 1 : 0);
          initialName = psiParameters.length > 0 ? psiParameters[idx < psiParameters.length ? idx : psiParameters.length - 1].getName()
                                                 : parameter.getName();
        }
        else {
          initialName = parameter.getName();
        }
        if ("_".equals(initialName)) {
          SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, psiSubstitutor.substitute(parameter.getType()));
          if (nameInfo.names.length > 0) {
            initialName = nameInfo.names[0];
          }
        }
        baseName = codeStyleManager.variableNameToPropertyName(initialName, VariableKind.PARAMETER);
      }

      String parameterName = nameGenerator.generateUniqueName(codeStyleManager.suggestUniqueVariableName(baseName, referenceExpression, true));
      map.put(parameter, parameterName);
      return parameterName;
    };
    @NonNls StringBuilder buf = new StringBuilder();
    if (parameters.length == 1) {
      buf.append(paramPresentationFunction.fun(parameters[0]));
    }
    else {
      buf.append("(").append(StringUtil.join(parameters, paramPresentationFunction, ", ")).append(")");
    }
    buf.append(" -> ");


    final JavaResolveResult resolveResult = referenceExpression.advancedResolve(false);
    final PsiElement resolveElement = resolveResult.getElement();

    if (resolveElement instanceof PsiMember) {
      buf.append("{");

      if (!PsiTypes.voidType().equals(interfaceMethod.getReturnType())) {
        buf.append("return ");
      }
      final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(referenceExpression);
      final PsiElement qualifier = referenceExpression.getQualifier();
      PsiClass containingClass = qualifierResolveResult.getContainingClass();

      final boolean onArrayRef = PsiUtil.isArrayClass(containingClass);

      final PsiElement referenceNameElement = referenceExpression.getReferenceNameElement();
      if (isReceiver){
        buf.append(map.get(parameters[0])).append(".");
      } else {
        if (!(referenceNameElement instanceof PsiKeyword)) {
          if (qualifier instanceof PsiTypeElement) {
            final PsiJavaCodeReferenceElement referenceElement = ((PsiTypeElement)qualifier).getInnermostComponentReferenceElement();
            if (referenceElement != null && !PsiTreeUtil.isAncestor(containingClass, referenceExpression, false)) {
              buf.append(referenceElement.getReferenceName()).append(".");
            }
          }
          else if (qualifier != null && !isQualifierUnnecessary(qualifier, containingClass)) {
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
          buf.append(getMemberQualifier((PsiMember)resolveElement));

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

      if (!onArrayRef || !(referenceNameElement instanceof PsiKeyword) || isReceiver) {
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
    return buf.toString();
  }

  private static @NotNull String getMemberQualifier(PsiMember member) {
    String qualifiedName = null;
    if (member instanceof PsiClass) {
      qualifiedName = ((PsiClass)member).getQualifiedName();
    }
    if (qualifiedName == null) {
      qualifiedName = Objects.requireNonNull(member.getName());
    }
    return qualifiedName;
  }

  private static boolean isQualifierUnnecessary(PsiElement qualifier, PsiClass containingClass) {
    if (qualifier instanceof PsiReferenceExpression reference && reference.resolve() instanceof PsiClass &&
        reference.getQualifier() == null && PsiTreeUtil.isContextAncestor(containingClass, qualifier, false)) {
      return true;
    }
    return qualifier instanceof PsiThisExpression thisExpression && thisExpression.getQualifier() == null;
  }

  private static boolean isInferredSameTypeAfterConversion(PsiLambdaExpression lambdaExpression,
                                                           PsiMethodReferenceExpression methodReferenceExpression) {
    PsiCall call = LambdaUtil.treeWalkUp(methodReferenceExpression);
    if (call == null) {
      return true;
    }
    Object marker = new Object();
    PsiTreeUtil.mark(methodReferenceExpression, marker);
    try {
      RecaptureTypeMapper.encode(methodReferenceExpression);
      PsiCall copyTopLevelCall = LambdaUtil.copyTopLevelCall(call);
      if (copyTopLevelCall != null) {
        PsiMethodReferenceExpression methodReferenceInCopy = (PsiMethodReferenceExpression)PsiTreeUtil.releaseMark(copyTopLevelCall, marker);
        if (methodReferenceInCopy != null) {
          PsiClassType functionalInterfaceType = ObjectUtils.tryCast(methodReferenceInCopy.getFunctionalInterfaceType(), PsiClassType.class);
          if (functionalInterfaceType == null) return false;
          PsiClassType.ClassResolveResult funcResult = functionalInterfaceType.resolveGenerics();
          PsiClass funcClass = funcResult.getElement();
          PsiSubstitutor funcSubstitutor = funcResult.getSubstitutor();
          PsiSubstitutor recapture = new RecaptureTypeMapper().recapture(funcSubstitutor);
          PsiLambdaExpression lambdaCopy = (PsiLambdaExpression)methodReferenceInCopy.replace(lambdaExpression);

          PsiClassType lambdaCopyType = (PsiClassType)lambdaCopy.getFunctionalInterfaceType();
          if (lambdaCopyType == null) return false;
          PsiClassType.ClassResolveResult lambdaCopyResult = lambdaCopyType.resolveGenerics();
          PsiClass lambdaCopyClass = lambdaCopyResult.getElement();
          PsiSubstitutor lambdaCopySubstitutor = lambdaCopyResult.getSubstitutor();
          return lambdaExpression.getManager().areElementsEquivalent(funcClass, lambdaCopyClass)
                 && recapture.equals(lambdaCopySubstitutor);
        }
      }
    }
    finally {
      RecaptureTypeMapper.clean(methodReferenceExpression);
    }
    return false;
  }

  public static void simplifyToExpressionLambda(@NotNull PsiLambdaExpression lambdaExpression) {
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
        if (ExpressionUtils.isNewObject(qualifierExpression)) {
          sideEffects.add(qualifierExpression);
        }
        else {
          SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
        }
        if (!sideEffects.isEmpty()) {
          if (ApplicationManager.getApplication().isUnitTestMode() ||
              Messages.showYesNoDialog(lambdaExpression.getProject(),
                                       JavaRefactoringBundle.message("lambda.to.reference.side.effect.warning.message"),
                                       JavaRefactoringBundle.message("side.effects.detected.title"), Messages.getQuestionIcon()) == Messages.YES) {
            //ensure introduced before lambda
            qualifierExpression.putUserData(ElementToWorkOn.PARENT, lambdaExpression);
            RefactoringSupportProvider supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage(JavaLanguage.INSTANCE);
            JavaIntroduceVariableHandlerBase handler = (JavaIntroduceVariableHandlerBase)supportProvider.getIntroduceVariableHandler();
            assert handler != null;
            handler.invoke(qualifierExpression.getProject(), editor, qualifierExpression);
          }
        }
      }
    }
  }

  /**
   * Checks whether method reference can be converted to lambda without significant semantics change
   * (i.e. method reference qualifier has no side effects)
   *
   * @param methodReferenceExpression method reference to check
   * @return true if method reference can be converted to lambda
   */
  public static boolean canConvertToLambdaWithoutSideEffects(PsiMethodReferenceExpression methodReferenceExpression) {
    if (!canConvertToLambda(methodReferenceExpression)) return false;
    final PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(methodReferenceExpression.getQualifierExpression());
    return qualifierExpression == null ||
           !SideEffectChecker.mayHaveSideEffects(qualifierExpression) && !ExpressionUtils.isNewObject(qualifierExpression);
  }
}
