// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author ven
 */
public class WrapExpressionFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(WrapExpressionFix.class);

  private final PsiExpression myExpression;
  private final @Nullable String myRole;
  private final PsiClassType myExpectedType;
  private final boolean myPrimitiveExpected;
  private final String myMethodPresentation;

  public WrapExpressionFix(@NotNull PsiType expectedType, @NotNull PsiExpression expression, @Nullable String role) {
    myExpression = expression;
    myRole = role;
    myExpectedType = getClassType(expectedType, expression);
    myPrimitiveExpected = expectedType instanceof PsiPrimitiveType;
    myMethodPresentation = getMethodPresentation(myExpression, myExpectedType, myPrimitiveExpected);
  }

  private WrapExpressionFix(PsiExpression expression,
                            PsiClassType expectedType,
                            boolean primitiveExpected,
                            String methodPresentation,
                            @Nullable String role) {
    myExpression = expression;
    myExpectedType = expectedType;
    myPrimitiveExpected = primitiveExpected;
    myMethodPresentation = methodPresentation;
    myRole = role;
  }

  @Nullable
  private static PsiClassType getClassType(PsiType type, PsiElement place) {
    if (type instanceof PsiClassType) {
      return (PsiClassType)type;
    }
    else if (type instanceof PsiPrimitiveType) {
      return ((PsiPrimitiveType)type).getBoxedType(place.getManager(), GlobalSearchScope.allScope(place.getProject()));
    }
    return null;
  }

  @Override
  @NotNull
  public String getText() {
    return myRole == null ? QuickFixBundle.message("wrap.expression.using.static.accessor.text", myMethodPresentation) :
           QuickFixBundle.message("wrap.expression.using.static.accessor.text.role", myMethodPresentation, myRole);
  }

  private static String getMethodPresentation(PsiExpression expression, PsiClassType expectedType, boolean primitiveExpected) {
    PsiType type = expression.getType();
    if (expectedType != null && type != null) {
      final PsiMethod wrapper = findWrapper(type, expectedType, primitiveExpected, expression);
      if (wrapper != null) {
        final PsiClass containingClass = wrapper.getContainingClass();
        if (containingClass != null) {
          return containingClass.getName() + '.' + wrapper.getName();
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiMethod findWrapper(@NotNull PsiType type, @NotNull PsiClassType expectedType,
                                       boolean primitiveExpected, @NotNull PsiElement context) {
    PsiClass aClass = expectedType.resolve();
    if (aClass != null) {
      PsiType expectedReturnType = expectedType;
      if (primitiveExpected) {
        expectedReturnType = PsiPrimitiveType.getUnboxedType(expectedType);
      }
      boolean isString = CommonClassNames.JAVA_LANG_STRING.equals(aClass.getQualifiedName());
      if (type instanceof PsiArrayType && isString) {
        aClass = PsiResolveHelper.getInstance(aClass.getProject()).resolveReferencedClass(CommonClassNames.JAVA_UTIL_ARRAYS, aClass);
        if (aClass == null) return null;
      }
      if (expectedReturnType == null) return null;
      PsiMethod[] methods = aClass.getMethods();
      final Set<PsiMethod> wrapperMethods = new LinkedHashSet<>();
      for (PsiMethod method : methods) {
        if (method.hasModifierProperty(PsiModifier.STATIC)
            && method.getParameterList().getParametersCount() == 1
            && Objects.requireNonNull(method.getParameterList().getParameter(0)).getType().isAssignableFrom(type)
            && method.getReturnType() != null
            && expectedReturnType.equals(method.getReturnType())) {
          final String methodName = method.getName();
          if (methodName.startsWith("parse") || methodName.equals("valueOf") || (isString && methodName.equals("toString"))) {
            return method;
          }
          wrapperMethods.add(method);
        }
      }
      if (!wrapperMethods.isEmpty()) return wrapperMethods.iterator().next();
    }

    return null;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("wrap.expression.using.static.accessor.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethodPresentation != null
           && myExpression.isValid()
           && BaseIntentionAction.canModify(myExpression)
           && PsiImplUtil.getSwitchLabel(myExpression) == null
           && myExpectedType != null
           && myExpectedType.isValid()
           && myExpression.getType() != null
           && findWrapper(myExpression.getType(), myExpectedType, myPrimitiveExpected, myExpression) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiType type = myExpression.getType();
    if (type == null) {
      LOG.error("Expression type is null");
      return;
    }
    PsiMethod wrapper = findWrapper(type, myExpectedType, myPrimitiveExpected, myExpression);
    if (wrapper == null) {
      LOG.error("Wrapper not found; expectedType = " + myExpectedType.getCanonicalText() + "; primitiveExpected = " + myPrimitiveExpected);
      return;
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(file.getProject());
    @NonNls String methodCallText = "Foo." + wrapper.getName() + "()";
    PsiMethodCallExpression call = (PsiMethodCallExpression)factory.createExpressionFromText(methodCallText,
                                                                                             null);
    call.getArgumentList().add(myExpression);
    PsiReferenceExpression qualifier = (PsiReferenceExpression)Objects.requireNonNull(call.getMethodExpression().getQualifierExpression());
    PsiClass wrapperClass = Objects.requireNonNull(wrapper.getContainingClass());
    qualifier.bindToElement(wrapperClass);
    myExpression.replace(call);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static void registerWrapAction(JavaResolveResult[] candidates,
                                        PsiExpression[] expressions,
                                        @NotNull HighlightInfo.Builder highlightInfo,
                                        TextRange fixRange) {
    PsiType expectedType = null;
    PsiExpression expr = null;

    nextMethod:
    for (int i = 0; i < candidates.length && expectedType == null; i++) {
      final JavaResolveResult candidate = candidates[i];
      final PsiSubstitutor substitutor = candidate.getSubstitutor();
      final PsiElement element = candidate.getElement();
      assert element != null;
      final PsiMethod method = (PsiMethod)element;
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (!method.isVarArgs() && parameters.length != expressions.length) continue;
      for (int j = 0; j < expressions.length; j++) {
        PsiExpression expression = expressions[j];
        final PsiType exprType = expression.getType();
        if (exprType != null && !PsiType.NULL.equals(exprType)) {
          PsiType paramType = parameters[Math.min(j, parameters.length - 1)].getType();
          if (paramType instanceof PsiEllipsisType) {
            paramType = ((PsiEllipsisType)paramType).getComponentType();
          }
          paramType = substitutor.substitute(paramType);
          if (paramType.isAssignableFrom(exprType)) continue;
          final PsiClassType classType = getClassType(paramType, expression);
          if (expectedType == null && classType != null && findWrapper(exprType, classType, paramType instanceof PsiPrimitiveType,
                                                                       expression) != null) {
            expectedType = paramType;
            expr = expression;
          }
          else {
            expectedType = null;
            expr = null;
            continue nextMethod;
          }
        }
      }
    }

    if (expectedType != null) {
      IntentionAction action = new WrapExpressionFix(expectedType, expr, null);
      highlightInfo.registerFix(action, null, null, fixRange, null);
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new WrapExpressionFix(PsiTreeUtil.findSameElementInCopy(myExpression, target),
                                 myExpectedType,
                                 myPrimitiveExpected,
                                 myMethodPresentation,
                                 myRole);
  }
}
