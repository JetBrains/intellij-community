/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 4/12/11 3:26 PM
 */
public class JavaFormatterUtil {

  private JavaFormatterUtil() {
  }

  public static boolean isFirstMethodCallArgument(@NotNull ASTNode node) {
    ASTNode firstArgCandidate = node;
    ASTNode expressionList = node.getTreeParent();
    if (expressionList == null) {
      return false;
    }
    
    if (expressionList.getElementType() != JavaElementType.EXPRESSION_LIST
        && expressionList.getElementType() == JavaElementType.NEW_EXPRESSION)
    {
      firstArgCandidate = expressionList;
      expressionList = expressionList.getTreeParent();
    }
    
    if (expressionList == null || expressionList.getElementType() != JavaElementType.EXPRESSION_LIST) {
      return false;
    }
    
    ASTNode methodCallExpression = expressionList.getTreeParent();
    if (methodCallExpression == null || methodCallExpression.getElementType() != JavaElementType.METHOD_CALL_EXPRESSION) {
      return false;
    }

    ASTNode lbrace = expressionList.getFirstChildNode();
    ASTNode firstArg = lbrace.getTreeNext();
    if (firstArg != null && ElementType.WHITE_SPACE_BIT_SET.contains(firstArg.getElementType())) {
      firstArg = firstArg.getTreeNext();
    }
    
    return firstArg == firstArgCandidate;
  }

  /**
   * Allows to check if given node references anonymous class instance used as a method call argument. The most important thing
   * is that that method call expression should have other anonymous classes as well.
   * <p/>
   * <b>Examples</b>
   * <pre>
   *   test(new Runnable() {         &lt;-- true is returned for this node
   *          public void run() {
   *          }
   *        },
   *        new Runnable() {          &lt;-- false is returned for this node
   *          public void run() {
   *          }
   *        }
   *    );
   *    
   *    test(1234, "text", new Runnable() {         &lt;-- true is returned for this node because there are no other anonymous
   *          public void run() {                          class objects at method call expression before it
   *          }
   *        },
   *        new Runnable() {                        &lt;-- false is returned for this node
   *          public void run() {
   *          }
   *        }
   *    );    
   *    
   *    test(1234, "text", new Runnable() {         &lt;-- false is returned for this node because there are no other anonymous
   *        public void run() {                            class objects at method call expression after it
   *        }
   *    });
   * </pre>
   * 
   * @param node      node to process
   * @return
   */
  public static boolean isFirstAmongOthersAnonymousClassMethodCallArguments(@NotNull ASTNode node) {
    ASTNode expressionList = node.getTreeParent();
    ASTNode firstAnonymousClassCandidate = node;
    if (expressionList == null) {
      return false;
    }

    if (expressionList.getElementType() != JavaElementType.EXPRESSION_LIST
        && expressionList.getElementType() == JavaElementType.NEW_EXPRESSION)
    {
      firstAnonymousClassCandidate = expressionList;
      expressionList = expressionList.getTreeParent();
    }

    if (expressionList == null || expressionList.getElementType() != JavaElementType.EXPRESSION_LIST) {
      return false;
    }

    ASTNode methodCallExpression = expressionList.getTreeParent();
    if (methodCallExpression == null || methodCallExpression.getElementType() != JavaElementType.METHOD_CALL_EXPRESSION) {
      return false;
    }

    ASTNode lbrace = expressionList.getFirstChildNode();
    boolean firstAnonymousClass = false;
    for (ASTNode arg = lbrace.getTreeNext(); arg != null; arg = FormattingAstUtil.getNextNonWhiteSpaceNode(arg)) {
      if (!isAnonymousClass(arg)) {
        continue;
      }
      if (firstAnonymousClass) {
        // Other anonymous class is found at the method call expression after the target one.
        return true;
      }
      else if (arg != firstAnonymousClassCandidate) {
        return false;
      }
      else {
        firstAnonymousClass = true;
      }
    }
    return false;
  }
  
  /**
   * Allows to check if given expression list has given number of anonymous classes.
   * 
   * @param count   interested number of anonymous classes used at the given expression list
   * @return        <code>true</code> if given expression list contains given number of anonymous classes;
   *                <code>false</code> otherwise
   */
  public static boolean hasAnonymousClassesArguments(@NotNull PsiExpressionList expressionList, int count) {
    int found = 0;
    for (PsiExpression expression : expressionList.getExpressions()) {
      ASTNode node = expression.getNode();
      if (isAnonymousClass(node)) {
        found++;
      }
      if (found >= count) {
        return true;
      }
    }
    return false;
  }
  
  private static boolean isAnonymousClass(@Nullable final ASTNode node) {
    if (node == null) {
      return false;
    }
    ASTNode nodeToCheck = node;
    if (node.getElementType() == JavaElementType.NEW_EXPRESSION) {
      nodeToCheck = node.getLastChildNode();
    }
    return nodeToCheck != null && nodeToCheck.getElementType() == JavaElementType.ANONYMOUS_CLASS;
  }
}
