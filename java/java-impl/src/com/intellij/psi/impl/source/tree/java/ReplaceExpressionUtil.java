/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;

public class ReplaceExpressionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ReplaceExpressionUtil");

  public static boolean isNeedParenthesis(ASTNode oldExpr, ASTNode newExpr) {
    final ASTNode oldParent = oldExpr.getTreeParent();
    if (!ElementType.EXPRESSION_BIT_SET.contains(oldParent.getElementType())) return false;
    int priority = getExpressionPriority(newExpr);
    int parentPriority = getExpressionPriority(oldParent);
    if (priority > parentPriority) return false;
    IElementType i = oldParent.getElementType();
    if (i == JavaElementType.ASSIGNMENT_EXPRESSION) {
      return priority < parentPriority || ((CompositeElement)oldParent).getChildRole(oldExpr) == ChildRole.LOPERAND;
    }
    else if (i == JavaElementType.CONDITIONAL_EXPRESSION) {
      int role = ((CompositeElement)oldParent).getChildRole(oldExpr);
      if (role == ChildRole.THEN_EXPRESSION) return false;
      return priority < parentPriority || role != ChildRole.ELSE_EXPRESSION;
    }
    else if (i == JavaElementType.BINARY_EXPRESSION) {
      if (priority < parentPriority) return true;
      final IElementType opType = ((PsiBinaryExpression)SourceTreeToPsiMap.treeElementToPsi(oldParent)).getOperationSign().getTokenType();
      return ((CompositeElement)oldParent).getChildRole(oldExpr) != ChildRole.LOPERAND &&
             opType != JavaTokenType.PLUS &&
             opType != JavaTokenType.ASTERISK &&
             opType != JavaTokenType.ANDAND &&
             opType != JavaTokenType.OROR;
    }
    else if (i == JavaElementType.INSTANCE_OF_EXPRESSION) {
      return priority < parentPriority;
    }
    else if (i == JavaElementType.PREFIX_EXPRESSION || i == JavaElementType.TYPE_CAST_EXPRESSION) {
      return priority < parentPriority;
    }
    else if (i == JavaElementType.POSTFIX_EXPRESSION) {
      return priority <= parentPriority;
    }
    else if (i == JavaElementType.REFERENCE_EXPRESSION) {
      return priority < parentPriority;
    }
    else if (i == JavaElementType.METHOD_CALL_EXPRESSION) {
      return false;
    }
    else if (i == JavaElementType.NEW_EXPRESSION) {
      return false;
    }
    else if (i == JavaElementType.ARRAY_ACCESS_EXPRESSION) {
      int role = ((CompositeElement)oldParent).getChildRole(oldExpr);
      return role != ChildRole.ARRAY_DIMENSION && priority < parentPriority;
    }
    else if (i == JavaElementType.ARRAY_INITIALIZER_EXPRESSION) {
      return false;
    }
    else if (i == JavaElementType.PARENTH_EXPRESSION) {
      return false;
    }
    else if (i == JavaElementType.LITERAL_EXPRESSION ||
             i == JavaElementType.THIS_EXPRESSION ||
             i == JavaElementType.SUPER_EXPRESSION ||
             i == JavaElementType.CLASS_OBJECT_ACCESS_EXPRESSION) {
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
    else if (i == JavaElementType.BINARY_EXPRESSION) {
      IElementType opType = ((PsiBinaryExpression)SourceTreeToPsiMap.treeElementToPsi(expr)).getOperationSign().getTokenType();
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
      else if (opType == JavaTokenType.LTLT || opType == JavaTokenType.GTGT || opType == JavaTokenType.GTGTGT) {
        return 9;
      }
      else if (opType == JavaTokenType.PLUS || opType == JavaTokenType.MINUS) {
        return 10;
      }
      else if (opType == JavaTokenType.ASTERISK || opType == JavaTokenType.DIV || opType == JavaTokenType.PERC) {
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
    else if (i == JavaElementType.POSTFIX_EXPRESSION) {
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
             i == JavaElementType.EMPTY_EXPRESSION) {
      return 14;
    }
    else {
      LOG.error("Unknown element type:" + i);
      return -1;
    }
  }
}
