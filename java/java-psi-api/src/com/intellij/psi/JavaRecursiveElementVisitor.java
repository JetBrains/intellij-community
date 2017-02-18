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

  @Override
  public void visitElement(PsiElement element) {
    if (!myRefExprsInVisit.isEmpty() && myRefExprsInVisit.peek() == element) {
      myRefExprsInVisit.pop();
      myRefExprsInVisit.push(null);
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
