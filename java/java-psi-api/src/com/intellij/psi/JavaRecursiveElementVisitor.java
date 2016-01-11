/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.util.containers.Stack;

/**
 * a JavaElementVisitor which also visits all children elements
 * in a tree pre-order, see <a href="https://en.wikipedia.org/wiki/Tree_traversal#Pre-order">Tree traversal:Pre-order</a> for details.
 * <p>
 * <b>Note</b>: This visitor handles all elements recursively, so it can consume a large amount of stack space for very deep trees.
 * For such deep trees please consider using {@link JavaRecursiveElementWalkingVisitor} instead.
 */
public abstract class JavaRecursiveElementVisitor extends JavaElementVisitor {
  // This stack thing is intended to prevent exponential child traversing due to visitReferenceExpression calls both visitRefElement
  // and visitExpression.
  private final Stack<PsiReferenceExpression> myRefExprsInVisit = new Stack<PsiReferenceExpression>();
  private final Stack<PsiBinaryExpression> myBinaryExpressions = new Stack<PsiBinaryExpression>();

  @Override
  public void visitElement(PsiElement element) {
    if (!myRefExprsInVisit.isEmpty() && myRefExprsInVisit.peek() == element) {
      myRefExprsInVisit.pop();
      myRefExprsInVisit.push(null);
    }
    else if (element instanceof PsiBinaryExpression) {
      //implement smart traversing to avoid stack overflow
      if (!myBinaryExpressions.isEmpty() && myBinaryExpressions.peek() == element) {
        return;
      }
      PsiElement child = element.getFirstChild();
      while (child != null) {
        if (child instanceof PsiBinaryExpression) {
          myBinaryExpressions.push((PsiBinaryExpression)child);
        }
        child.accept(this);
        child = child.getNextSibling();
        if (child == null) {
          child = myBinaryExpressions.isEmpty() ? null : myBinaryExpressions.pop();
          if (child != null) child = child.getFirstChild();
        }
      }
    }
    else {
      element.acceptChildren(this);
    }
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    myRefExprsInVisit.push(expression);
    try {
      visitExpression(expression);
      visitReferenceElement(expression);
    }
    finally {
      myRefExprsInVisit.pop();
    }
  }
}
