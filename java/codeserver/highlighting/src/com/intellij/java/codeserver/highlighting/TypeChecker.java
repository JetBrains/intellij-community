// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

final class TypeChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  TypeChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkIllegalType(@NotNull PsiTypeElement typeElement) {
    PsiElement parent = typeElement.getParent();
    if (parent instanceof PsiTypeElement) return;

    if (PsiUtil.isInsideJavadocComment(typeElement)) return;

    PsiType type = typeElement.getType();
    PsiType componentType = type.getDeepComponentType();
    if (componentType instanceof PsiClassType) {
      PsiClass aClass = PsiUtil.resolveClassInType(componentType);
      if (aClass == null) {
        if (typeElement.isInferredType() && parent instanceof PsiLocalVariable localVariable) {
          PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(localVariable.getInitializer());
          if (initializer instanceof PsiNewExpression) {
            // The problem is already reported on the initializer
            return;
          }
        }
        if (myVisitor.isIncompleteModel()) return;
        myVisitor.report(JavaErrorKinds.TYPE_UNKNOWN_CLASS.create(typeElement));
      }
    }
  }

  void checkVarTypeApplicability(@NotNull PsiTypeElement typeElement) {
    if (!typeElement.isInferredType()) return;
    PsiElement parent = typeElement.getParent();
    PsiVariable variable = tryCast(parent, PsiVariable.class);
    if (variable instanceof PsiLocalVariable localVariable) {
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        if (PsiUtilCore.hasErrorElementChild(variable)) return;
        myVisitor.report(JavaErrorKinds.LVTI_NO_INITIALIZER.create(localVariable));
        return;
      }
      PsiExpression deparen = PsiUtil.skipParenthesizedExprDown(initializer);
      if (deparen instanceof PsiFunctionalExpression) {
        var kind = deparen instanceof PsiLambdaExpression ? JavaErrorKinds.LVTI_LAMBDA : JavaErrorKinds.LVTI_METHOD_REFERENCE;
        myVisitor.report(kind.create(localVariable));
        return;
      }

      if (ExpressionChecker.isArrayDeclaration(variable)) {
        myVisitor.report(JavaErrorKinds.LVTI_ARRAY.create(localVariable));
        return;
      }

      PsiType lType = variable.getType();
      if (PsiTypes.nullType().equals(lType) && allChildrenAreNullLiterals(initializer)) {
        myVisitor.report(JavaErrorKinds.LVTI_NULL.create(localVariable));
        return;
      }
      if (PsiTypes.voidType().equals(lType)) {
        myVisitor.report(JavaErrorKinds.LVTI_VOID.create(localVariable));
      }
    }
    else if (variable instanceof PsiParameter && variable.getParent() instanceof PsiParameterList && 
             ExpressionChecker.isArrayDeclaration(variable)) {
      myVisitor.report(JavaErrorKinds.LVTI_ARRAY.create(variable));
    }
  }

  void checkVariableInitializerType(@NotNull PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    // array initializer checked in checkArrayInitializerApplicable
    if (initializer == null || initializer instanceof PsiArrayInitializerExpression) return;
    PsiType lType = variable.getType();
    PsiType rType = initializer.getType();
    myVisitor.myExpressionChecker.checkAssignability(lType, rType, initializer, initializer);
  }

  void checkVarTypeApplicability(@NotNull PsiVariable variable) {
    if (variable instanceof PsiLocalVariable local && variable.getTypeElement().isInferredType()) {
      PsiElement parent = variable.getParent();
      if (parent instanceof PsiDeclarationStatement statement && statement.getDeclaredElements().length > 1) {
        myVisitor.report(JavaErrorKinds.LVTI_COMPOUND.create(local));
      }
    }
  }

  private static boolean allChildrenAreNullLiterals(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return false;
    if (expression instanceof PsiLiteralExpression literal && PsiTypes.nullType().equals(literal.getType())) return true;
    if (expression instanceof PsiTypeCastExpression cast) {
      return allChildrenAreNullLiterals(cast.getOperand());
    }
    if (expression instanceof PsiConditionalExpression conditional) {
      return allChildrenAreNullLiterals(conditional.getThenExpression()) &&
             allChildrenAreNullLiterals(conditional.getElseExpression());
    }
    if (expression instanceof PsiSwitchExpression switchExpression) {
      PsiCodeBlock switchBody = switchExpression.getBody();
      if (switchBody == null) return false;
      PsiStatement[] statements = switchBody.getStatements();
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiSwitchLabeledRuleStatement rule) {
          PsiStatement ruleBody = rule.getBody();
          if (ruleBody instanceof PsiBlockStatement blockStatement) {
            for (PsiYieldStatement yield : PsiTreeUtil.findChildrenOfType(blockStatement, PsiYieldStatement.class)) {
              if (yield.findEnclosingExpression() == switchExpression && !allChildrenAreNullLiterals(yield.getExpression())) { 
                return false;
              }
            }
          }
          else if (ruleBody instanceof PsiExpressionStatement expr) {
            if (!allChildrenAreNullLiterals(expr.getExpression())) return false;
          }
        }
        else if (statement instanceof PsiYieldStatement yield) {
          if (!allChildrenAreNullLiterals(yield.getExpression())) return false;
        }
        else {
          for (PsiYieldStatement yield : PsiTreeUtil.findChildrenOfType(statement, PsiYieldStatement.class)) {
            if (yield.findEnclosingExpression() == switchExpression && !allChildrenAreNullLiterals(yield.getExpression())) {
              return false;
            }
          }
        }
      }
      return true;
    }
    return false;
  }

  void checkArrayType(@NotNull PsiTypeElement type) {
    int dimensions = 0;
    for (PsiElement child = type.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
        dimensions++;
      }
    }
    if (dimensions > 255) {
      // JVM Specification, 4.3.2: no more than 255 dimensions allowed
      myVisitor.report(JavaErrorKinds.ARRAY_TOO_MANY_DIMENSIONS.create(type));
    }
  }

  void checkIllegalVoidType(@NotNull PsiKeyword type) {
    if (!JavaKeywords.VOID.equals(type.getText())) return;

    PsiElement parent = type.getParent();
    if (parent instanceof PsiErrorElement) return;
    if (parent instanceof PsiTypeElement) {
      PsiElement typeOwner = parent.getParent();
      if (typeOwner != null) {
        // do not highlight incomplete declarations
        if (PsiUtilCore.hasErrorElementChild(typeOwner)) return;
      }

      if (typeOwner instanceof PsiMethod method) {
        if (method.getReturnTypeElement() == parent && PsiTypes.voidType().equals(method.getReturnType())) return;
      }
      else if (typeOwner instanceof PsiClassObjectAccessExpression classAccess) {
        if (TypeConversionUtil.isVoidType(classAccess.getOperand().getType())) return;
      }
      else if (typeOwner instanceof JavaCodeFragment) {
        if (typeOwner.getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null) return;
      }
    }
    myVisitor.report(JavaErrorKinds.TYPE_VOID_ILLEGAL.create(type));
  }

  void checkMustBeThrowable(@NotNull PsiElement context, PsiType type) {
    PsiElementFactory factory = myVisitor.factory();
    PsiClassType throwable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, context.getResolveScope());
    if (type != null && !TypeConversionUtil.isAssignable(throwable, type) &&
        !(myVisitor.isIncompleteModel() && IncompleteModelUtil.isPotentiallyConvertible(throwable, type, context))) {
      myVisitor.reportIncompatibleType(throwable, type, context);
    }
  }
}
