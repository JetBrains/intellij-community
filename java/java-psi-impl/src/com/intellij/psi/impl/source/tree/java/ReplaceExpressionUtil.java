// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class ReplaceExpressionUtil {
  private static final Logger LOG = Logger.getInstance(ReplaceExpressionUtil.class);

  public static boolean isNeedParenthesis(@NotNull ASTNode oldExpr, ASTNode newExpr) {
    final ASTNode oldParent = oldExpr.getTreeParent();
    if (!ElementType.EXPRESSION_BIT_SET.contains(oldParent.getElementType())) return false;
    int priority = getExpressionPriority(newExpr);
    int parentPriority = getExpressionPriority(oldParent);
    if (priority == -1 || parentPriority == -1) {
      // Unknown element types: enclose with parentheses just in case
      return true;
    }
    if (priority > parentPriority) return false;
    IElementType i = oldParent.getElementType();
    if (i == JavaElementType.ASSIGNMENT_EXPRESSION) {
      return priority < parentPriority || ((CompositeElement)oldParent).getChildRole(oldExpr) == ChildRole.LOPERAND;
    }
    if (i == JavaElementType.CONDITIONAL_EXPRESSION) {
      int role = ((CompositeElement)oldParent).getChildRole(oldExpr);
      if (role == ChildRole.THEN_EXPRESSION) return false;
      return priority < parentPriority || role != ChildRole.ELSE_EXPRESSION;
    }
    if (i == JavaElementType.BINARY_EXPRESSION || i == JavaElementType.POLYADIC_EXPRESSION) {
      if (priority < parentPriority) return true;
      PsiElement element = SourceTreeToPsiMap.treeElementToPsi(oldParent);
      assert element != null;
      IElementType opType = ((PsiPolyadicExpression)element).getOperationTokenType();
      IElementType newI = newExpr.getElementType();
      if (((CompositeElement)oldParent).getChildRole(oldExpr) == ChildRole.LOPERAND) return false;
      if (newI == JavaElementType.BINARY_EXPRESSION || newI == JavaElementType.POLYADIC_EXPRESSION) {
        IElementType newType = ((PsiPolyadicExpression)newExpr).getOperationTokenType();
        if (newType == JavaTokenType.DIV || newType == JavaTokenType.PERC) return true;
      }
      return opType != JavaTokenType.PLUS &&
             opType != JavaTokenType.ASTERISK &&
             opType != JavaTokenType.ANDAND &&
             opType != JavaTokenType.OROR;
    }
    if (i == JavaElementType.POSTFIX_EXPRESSION) {
      return true;
    }
    if (i == JavaElementType.INSTANCE_OF_EXPRESSION ||
             i == JavaElementType.PREFIX_EXPRESSION ||
             i == JavaElementType.TYPE_CAST_EXPRESSION ||
             i == JavaElementType.REFERENCE_EXPRESSION ||
             i == JavaElementType.METHOD_REF_EXPRESSION) {
      return priority < parentPriority;
    }
    if (i == JavaElementType.ARRAY_ACCESS_EXPRESSION) {
      int role = ((CompositeElement)oldParent).getChildRole(oldExpr);
      return role != ChildRole.ARRAY_DIMENSION && role != ChildRole.INDEX && priority < parentPriority;
    }
    if (i == JavaElementType.METHOD_CALL_EXPRESSION ||
             i == JavaElementType.NEW_EXPRESSION ||
             i == JavaElementType.ARRAY_INITIALIZER_EXPRESSION ||
             i == JavaElementType.PARENTH_EXPRESSION ||
             i == JavaElementType.LITERAL_EXPRESSION ||
             i == JavaElementType.THIS_EXPRESSION ||
             i == JavaElementType.SUPER_EXPRESSION ||
             i == JavaElementType.CLASS_OBJECT_ACCESS_EXPRESSION ||
             i == JavaElementType.LAMBDA_EXPRESSION ||
             i == JavaElementType.SWITCH_EXPRESSION) {
      return false;
    }

    LOG.assertTrue(false);
    return false;
  }

  private static int getExpressionPriority(ASTNode expr) {
    IElementType i = expr.getElementType();
    if (i == JavaElementType.ASSIGNMENT_EXPRESSION) {
      return 0;
    }
    else if (i == JavaElementType.CONDITIONAL_EXPRESSION) {
      return 1;
    }
    else if (i == JavaElementType.BINARY_EXPRESSION || i == JavaElementType.POLYADIC_EXPRESSION) {
      PsiElement element = SourceTreeToPsiMap.treeElementToPsi(expr);
      IElementType opType = ((PsiPolyadicExpression)element).getOperationTokenType();
      if (opType == JavaTokenType.OROR) {
        return 2;
      }
      else if (opType == JavaTokenType.ANDAND) {
        return 3;
      }
      else if (opType == JavaTokenType.OR) {
        return 4;
      }
      else if (opType == JavaTokenType.XOR) {
        return 5;
      }
      else if (opType == JavaTokenType.AND) {
        return 6;
      }
      else if (opType == JavaTokenType.EQEQ || opType == JavaTokenType.NE) {
        return 7;
      }
      else if (opType == JavaTokenType.LT || opType == JavaTokenType.GT || opType == JavaTokenType.LE || opType == JavaTokenType.GE) {
        return 8;
      }
      else if (ExpressionParser.SHIFT_OPS.contains(opType)) {
        return 9;
      }
      else if (ExpressionParser.ADDITIVE_OPS.contains(opType)) {
        return 10;
      }
      else if (ExpressionParser.MULTIPLICATIVE_OPS.contains(opType)) {
        return 11;
      }
      return 8;
    }
    else if (i == JavaElementType.INSTANCE_OF_EXPRESSION) {
      return 8;
    }
    else if (i == JavaElementType.PREFIX_EXPRESSION || i == JavaElementType.TYPE_CAST_EXPRESSION) {
      return 12;
    }
    else if (i == JavaElementType.POSTFIX_EXPRESSION || i == JavaElementType.SWITCH_EXPRESSION) {
      return 13;
    }
    else if (i == JavaElementType.LITERAL_EXPRESSION ||
             i == JavaElementType.REFERENCE_EXPRESSION ||
             i == JavaElementType.THIS_EXPRESSION ||
             i == JavaElementType.SUPER_EXPRESSION ||
             i == JavaElementType.PARENTH_EXPRESSION ||
             i == JavaElementType.METHOD_CALL_EXPRESSION ||
             i == JavaElementType.CLASS_OBJECT_ACCESS_EXPRESSION ||
             i == JavaElementType.NEW_EXPRESSION ||
             i == JavaElementType.ARRAY_ACCESS_EXPRESSION ||
             i == JavaElementType.ARRAY_INITIALIZER_EXPRESSION ||
             i == JavaElementType.JAVA_CODE_REFERENCE ||
             i == JavaElementType.METHOD_REF_EXPRESSION ||
             i == JavaElementType.LAMBDA_EXPRESSION ||
             i == JavaElementType.EMPTY_EXPRESSION) {
      return 14;
    }
    else {
      return -1;
    }
  }
}
