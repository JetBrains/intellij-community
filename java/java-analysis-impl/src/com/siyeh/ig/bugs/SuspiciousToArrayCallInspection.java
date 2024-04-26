// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SuspiciousToArrayCallInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final PsiType foundType = (PsiType)infos[1];
    return InspectionGadgetsBundle.message("suspicious.to.array.call.problem.descriptor", type.getCanonicalText(), foundType.getCanonicalText());
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new SuspiciousToArrayCallFix((PsiType)infos[0], (boolean)infos[2]);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousToArrayCallVisitor();
  }

  private static class SuspiciousToArrayCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"toArray".equals(methodName)) {
        return;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      final PsiClassType classType = ObjectUtils.tryCast(qualifierExpression.getType(), PsiClassType.class);
      if (classType == null || classType.isRaw()) return;
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      if (argument == null) return;

      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return;
      }
      if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(classType, expression.getResolveScope());
        PsiType argumentType = argument.getType();
        if (!(argumentType instanceof PsiArrayType)) {
          argumentType = getIntFunctionParameterType(argument);
        }
        checkArrayTypes(argument, expression, argumentType, itemType);
      }
      else if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_STREAM)) {
        PsiType argumentType = getIntFunctionParameterType(argument);
        if (argumentType != null) {
          checkArrayTypes(argument, expression, argumentType, getStreamElementType(qualifierExpression));
        }
      }
    }

    private static final CallMatcher STREAM_FILTER = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "filter")
      .parameterTypes(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE);
    private static final CallMatcher CLASS_INSTANCEOF = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_CLASS, "isInstance")
      .parameterCount(1);

    /**
     * @param expression stream expression
     * @return type of elements inside the stream. Tries to take into account previous filters by element type
     */
    private static @Nullable PsiType getStreamElementType(PsiExpression expression) {
      PsiMethodCallExpression call =
        ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiMethodCallExpression.class);
      while (STREAM_FILTER.test(call)) {
        PsiExpression predicate = PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
        if (predicate instanceof PsiMethodReferenceExpression) {
          if (CLASS_INSTANCEOF.methodReferenceMatches((PsiMethodReferenceExpression)predicate)) {
            PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(((PsiMethodReferenceExpression)predicate).getQualifierExpression());
            if (qualifier instanceof PsiClassObjectAccessExpression) {
              return ((PsiClassObjectAccessExpression)qualifier).getOperand().getType();
            }
          }
        }
        else if (predicate instanceof PsiLambdaExpression) {
          PsiParameter[] parameters = ((PsiLambdaExpression)predicate).getParameterList().getParameters();
          if (parameters.length == 1) {
            PsiExpression lambdaBody =
              PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(((PsiLambdaExpression)predicate).getBody()));
            if (lambdaBody instanceof PsiInstanceOfExpression &&
                ExpressionUtils.isReferenceTo(((PsiInstanceOfExpression)lambdaBody).getOperand(), parameters[0])) {
              PsiTypeElement checkType = ((PsiInstanceOfExpression)lambdaBody).getCheckType();
              if (checkType != null) {
                return checkType.getType();
              }
            }
          }
        }
        call = MethodCallUtils.getQualifierMethodCall(call);
      }
      return StreamApiUtil.getStreamElementType(expression.getType(), false);
    }

    private static PsiType getIntFunctionParameterType(PsiExpression argument) {
      PsiType argumentType = FunctionalExpressionUtils.getFunctionalExpressionType(argument);
      return PsiUtil.substituteTypeParameter(argumentType, "java.util.function.IntFunction", 0, false);
    }

    private void checkArrayTypes(@NotNull PsiExpression argument,
                                 @NotNull PsiMethodCallExpression expression,
                                 PsiType argumentType,
                                 PsiType itemType) {
      if (!(argumentType instanceof PsiArrayType arrayType)) {
        return;
      }
      final PsiType componentType = arrayType.getComponentType();
      PsiType actualType = getActualItemTypeIfMismatch(arrayType, expression, itemType);
      if (actualType != null) {
        registerError(argument, actualType, componentType, !(argument.getType() instanceof PsiArrayType));
      }
    }

    @Nullable
    private static PsiType getActualItemTypeIfMismatch(@NotNull PsiArrayType arrayType,
                                                       @NotNull PsiMethodCallExpression expression,
                                                       PsiType itemType) {
      itemType = GenericsUtil.getVariableTypeByExpressionType(itemType);
      final PsiType componentType = arrayType.getComponentType();
      if (itemType == null || componentType.isAssignableFrom(itemType)) return null;
      PsiClass componentClass = PsiUtil.resolveClassInClassTypeOnly(componentType);
      if (componentClass instanceof PsiTypeParameter) {
        final PsiReferenceList extendsList = ((PsiTypeParameter)componentClass).getExtendsList();
        final PsiClassType[] types = extendsList.getReferencedTypes();
        if (types.length == 0 || types.length == 1 && types[0].isAssignableFrom(itemType)) return null;
      }
      if (itemType instanceof PsiClassType) {
        final PsiClass aClass = ((PsiClassType)itemType).resolve();
        if (aClass instanceof PsiTypeParameter) {
          final PsiReferenceList extendsList = ((PsiTypeParameter)aClass).getExtendsList();
          final PsiClassType[] types = extendsList.getReferencedTypes();
          if (types.length == 0) {
            return TypeUtils.getObjectType(expression);
          }
          if (types.length == 1) {
            return types[0];
          }
          return null;
        }
      }
      return itemType;
    }
  }

  private static class SuspiciousToArrayCallFix extends PsiUpdateModCommandQuickFix {
    @NonNls private final String myReplacement;
    @NonNls private final String myPresented;
    
    SuspiciousToArrayCallFix(PsiType wantedType, boolean isFunction) {
      if (wantedType instanceof PsiClassType) {
        wantedType = ((PsiClassType)wantedType).rawType();
      }
      if (isFunction) {
        myReplacement = wantedType.getCanonicalText() + "[]::new";
        myPresented = wantedType.getPresentableText() + "[]::new";
      } else {
        final String index = StringUtil.repeat("[0]", wantedType.getArrayDimensions() + 1);
        final PsiType componentType = wantedType.getDeepComponentType();
        myReplacement = "new " + componentType.getCanonicalText() + index;
        myPresented = "new " + componentType.getPresentableText() + index;
      }
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiExpression expression = ObjectUtils.tryCast(startElement, PsiExpression.class);
      if (expression == null) return;
      new CommentTracker().replaceAndRestoreComments(expression, myReplacement);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myPresented);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("suspicious.to.array.call.fix.family.name");
    }
  }
}