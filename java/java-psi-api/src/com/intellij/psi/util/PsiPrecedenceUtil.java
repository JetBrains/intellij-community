// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class PsiPrecedenceUtil {
  public static final int PARENTHESIZED_PRECEDENCE = 0;
  public static final int LITERAL_PRECEDENCE = 0;
  public static final int METHOD_CALL_PRECEDENCE = 1;
  public static final int POSTFIX_PRECEDENCE = 2;
  public static final int PREFIX_PRECEDENCE = 3;
  public static final int TYPE_CAST_PRECEDENCE = 4;
  public static final int MULTIPLICATIVE_PRECEDENCE = 5;
  public static final int ADDITIVE_PRECEDENCE = 6;
  public static final int SHIFT_PRECEDENCE = 7;
  public static final int RELATIONAL_PRECEDENCE = 8;
  public static final int EQUALITY_PRECEDENCE = 9;
  public static final int BINARY_AND_PRECEDENCE = 10;
  public static final int BINARY_XOR_PRECEDENCE = 11;
  public static final int BINARY_OR_PRECEDENCE = 12;
  public static final int AND_PRECEDENCE = 13;
  public static final int OR_PRECEDENCE = 14;
  public static final int CONDITIONAL_PRECEDENCE = 15;
  public static final int ASSIGNMENT_PRECEDENCE = 16;
  public static final int LAMBDA_PRECEDENCE = 17; // jls-15.2
  public static final int NUM_PRECEDENCES = 18;

  private static final Map<IElementType, Integer> s_binaryOperatorPrecedence = new HashMap<>(NUM_PRECEDENCES);

  static {
    s_binaryOperatorPrecedence.put(JavaTokenType.PLUS, ADDITIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.MINUS, ADDITIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.ASTERISK, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.DIV, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.PERC, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.ANDAND, AND_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.OROR, OR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.AND, BINARY_AND_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.OR, BINARY_OR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.XOR, BINARY_XOR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.LTLT, SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.GTGT, SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.GTGTGT, SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.GT, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.GE, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.LT, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.LE, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.EQEQ, EQUALITY_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.NE, EQUALITY_PRECEDENCE);
  }

  public static boolean isCommutativeOperator(@NotNull IElementType token) {
    return token == JavaTokenType.PLUS || token == JavaTokenType.ASTERISK ||
           token == JavaTokenType.EQEQ || token == JavaTokenType.NE ||
           token == JavaTokenType.AND || token == JavaTokenType.OR || token == JavaTokenType.XOR;
  }

  public static boolean isCommutativeOperation(PsiPolyadicExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    if (!isCommutativeOperator(tokenType)) {
      return false;
    }
    final PsiType type = expression.getType();
    return type != null && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }

  public static boolean isAssociativeOperation(PsiPolyadicExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    final PsiType type = expression.getType();
    final PsiPrimitiveType primitiveType;
    if (type instanceof PsiClassType) {
      primitiveType = PsiPrimitiveType.getUnboxedType(type);
      if (primitiveType == null) {
        return false;
      }
    }
    else if (type instanceof PsiPrimitiveType) {
      primitiveType = (PsiPrimitiveType)type;
    }
    else {
      return false;
    }

    if (JavaTokenType.PLUS == tokenType || JavaTokenType.ASTERISK == tokenType) {
      return !PsiTypes.floatType().equals(primitiveType) && !PsiTypes.doubleType().equals(primitiveType);
    }
    else if (JavaTokenType.EQEQ == tokenType || JavaTokenType.NE == tokenType) {
      return PsiTypes.booleanType().equals(primitiveType);
    }
    else if (JavaTokenType.AND == tokenType || JavaTokenType.OR == tokenType || JavaTokenType.XOR == tokenType) {
      return true;
    }
    else if (JavaTokenType.OROR == tokenType || JavaTokenType.ANDAND == tokenType) {
      return true;
    }
    return false;
  }

  public static int getPrecedence(PsiExpression expression) {
    if (expression instanceof PsiThisExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiSuperExpression ||
        expression instanceof PsiClassObjectAccessExpression ||
        expression instanceof PsiArrayAccessExpression ||
        expression instanceof PsiArrayInitializerExpression) {
      return LITERAL_PRECEDENCE;
    }
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      if (referenceExpression.getQualifier() != null) {
        return METHOD_CALL_PRECEDENCE;
      }
      else {
        return LITERAL_PRECEDENCE;
      }
    }
    if (expression instanceof PsiMethodCallExpression || expression instanceof PsiNewExpression) {
      return METHOD_CALL_PRECEDENCE;
    }
    if (expression instanceof PsiTypeCastExpression) {
      return TYPE_CAST_PRECEDENCE;
    }
    if (expression instanceof PsiPrefixExpression) {
      return PREFIX_PRECEDENCE;
    }
    if (expression instanceof PsiPostfixExpression || expression instanceof PsiSwitchExpression) {
      return POSTFIX_PRECEDENCE;
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      return getPrecedenceForOperator(polyadicExpression.getOperationTokenType());
    }
    if (expression instanceof PsiInstanceOfExpression) {
      return RELATIONAL_PRECEDENCE;
    }
    if (expression instanceof PsiConditionalExpression) {
      return CONDITIONAL_PRECEDENCE;
    }
    if (expression instanceof PsiAssignmentExpression) {
      return ASSIGNMENT_PRECEDENCE;
    }
    if (expression instanceof PsiParenthesizedExpression) {
      return PARENTHESIZED_PRECEDENCE;
    }
    if (expression instanceof PsiLambdaExpression) {
      return LAMBDA_PRECEDENCE;
    }
    return -1;
  }

  public static int getPrecedenceForOperator(@NotNull IElementType operator) {
    final Integer precedence = s_binaryOperatorPrecedence.get(operator);
    if (precedence == null) {
      throw new IllegalArgumentException("unknown operator: " + operator);
    }
    return precedence.intValue();
  }

  public static boolean areParenthesesNeeded(PsiParenthesizedExpression expression, boolean ignoreClarifyingParentheses) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpression)) {
      return false;
    }
    final PsiExpression child = expression.getExpression();
    return child == null || areParenthesesNeeded(child, (PsiExpression)parent, ignoreClarifyingParentheses);
  }

  public static boolean areParenthesesNeeded(PsiExpression expression,
                                             PsiExpression parentExpression,
                                             boolean ignoreClarifyingParentheses) {
    if (parentExpression instanceof PsiParenthesizedExpression || parentExpression instanceof PsiArrayInitializerExpression) {
      return false;
    }
    if (parentExpression instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)parentExpression;
      return PsiTreeUtil.isAncestor(arrayAccessExpression.getArrayExpression(), expression, false);
    }
    final int parentPrecedence = getPrecedence(parentExpression);
    final int childPrecedence = getPrecedence(expression);
    if (parentPrecedence > childPrecedence) {
      if (ignoreClarifyingParentheses) {
        if (expression instanceof PsiPolyadicExpression) {
          return parentExpression instanceof PsiPolyadicExpression ||
                 parentExpression instanceof PsiConditionalExpression ||
                 parentExpression instanceof PsiInstanceOfExpression;
        }
        else return expression instanceof PsiInstanceOfExpression;
      }
      return false;
    }
    if (parentExpression instanceof PsiPolyadicExpression && expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression parentPolyadicExpression = (PsiPolyadicExpression)parentExpression;
      final PsiType parentType = parentPolyadicExpression.getType();
      if (parentType == null) {
        return true;
      }
      final PsiPolyadicExpression childPolyadicExpression = (PsiPolyadicExpression)expression;
      final PsiType childType = childPolyadicExpression.getType();
      if (!parentType.equals(childType)) {
        return true;
      }
      if (childType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return areStringParenthesesNeeded(childPolyadicExpression, parentPolyadicExpression, null);
      }
      else if (childType.equals(PsiTypes.booleanType())) {
        final PsiExpression[] operands = childPolyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          PsiType operandType = operand.getType();
          if (operandType != null && !PsiTypes.booleanType().equals(operandType)) {
            return true;
          }
        }
      }
      final IElementType parentOperator = parentPolyadicExpression.getOperationTokenType();
      final IElementType childOperator = childPolyadicExpression.getOperationTokenType();
      if (ignoreClarifyingParentheses) {
        if (!childOperator.equals(parentOperator)) {
          return true;
        }
      }
      final PsiExpression[] parentOperands = parentPolyadicExpression.getOperands();
      if (!PsiTreeUtil.isAncestor(parentOperands[0], expression, false)) {
        if (!isAssociativeOperation(parentPolyadicExpression) ||
            JavaTokenType.DIV == childOperator || JavaTokenType.PERC == childOperator) {
          return true;
        }
      }
    }
    else if (parentExpression instanceof PsiConditionalExpression && expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)parentExpression;
      final PsiExpression condition = conditionalExpression.getCondition();
      return PsiTreeUtil.isAncestor(condition, expression, true) || ignoreClarifyingParentheses;
    }
    else if (expression instanceof PsiLambdaExpression) { // jls-15.16
      if (parentExpression instanceof PsiTypeCastExpression) {
        return false;
      }
      else if (parentExpression instanceof PsiConditionalExpression) { // jls-15.25
        final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)parentExpression;
        return PsiTreeUtil.isAncestor(conditionalExpression.getCondition(), expression, true);
      }
    }
    return parentPrecedence < childPrecedence;
  }

  /**
   * Determines if parentheses are needed around the specified {@code childStringConcatenation} to retain
   * the same semantics (i.e. if removing the parentheses will change the behaviour of the code).
   *
   * @param childStringConcatenation  a string concatenation which is or is going to be used as an operand of
   *                                  the parent string concatenation.
   * @param parentStringConcatenation  a string concatenation
   * @param anchor  the operand of the {@code parentStringConcatenation} that contains or will be replaced
   *                with the childStringConcatenation. If null, {@code childStringConcatenation} will be
   *                used as {@code anchor}.
   * @return {@code true}, if parentheses are required, {@code false} otherwise.
   */
  public static boolean areStringParenthesesNeeded(@NotNull PsiPolyadicExpression childStringConcatenation,
                                                   @NotNull PsiPolyadicExpression parentStringConcatenation,
                                                   @Nullable PsiElement anchor) {
    PsiExpression[] childOperands = childStringConcatenation.getOperands();
    PsiType firstType = childOperands[0].getType();
    if (firstType != null && firstType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return false;
    }
    PsiType secondType = childOperands[1].getType();
    if (secondType != null && !secondType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return true;
    }
    PsiExpression[] parentOperands = parentStringConcatenation.getOperands();
    PsiType firstParentType = parentOperands[0].getType();
    if (firstParentType != null && firstParentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return false;
    }
    if (anchor == null) {
      anchor = childStringConcatenation;
    }
    for (int i = 1; i < parentOperands.length; i++) {
      PsiExpression operand = parentOperands[i];
      if (PsiTreeUtil.isAncestor(operand, anchor, false)) {
        return true;
      }
      final PsiType type = operand.getType();
      if (type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return false;
      }
    }
    return false;
  }

  public static boolean areParenthesesNeeded(PsiJavaToken compoundAssignmentToken, PsiExpression rhs) {
    if (rhs instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)rhs;
      final int precedence1 = getPrecedenceForOperator(binaryExpression.getOperationTokenType());
      final IElementType signTokenType = compoundAssignmentToken.getTokenType();
      final IElementType newOperatorToken = TypeConversionUtil.convertEQtoOperation(signTokenType);
      final int precedence2 = getPrecedenceForOperator(newOperatorToken);
      return precedence1 >= precedence2 || !isCommutativeOperator(newOperatorToken);
    }
    else {
      return rhs instanceof PsiConditionalExpression ||
             rhs instanceof PsiAssignmentExpression ||
             rhs instanceof PsiInstanceOfExpression;
    }
  }
}
