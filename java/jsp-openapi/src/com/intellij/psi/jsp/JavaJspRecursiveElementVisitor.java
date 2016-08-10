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
package com.intellij.psi.jsp;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.util.containers.Stack;

/**
 * @author yole
 */
public abstract class JavaJspRecursiveElementVisitor extends JavaJspElementVisitor {
  // This stack thing is intended to prevent exponential child traversing due to visitReferenceExpression calls both visitRefElement
  // and visitExpression.
  private final Stack<PsiReferenceExpression> myRefExprsInVisit = new Stack<>();

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

  //override in order to visit each root directly in visitor
  @Override public void visitJspFile(JspFile file) {
    super.visitJspFile(file);
    visitClass(file.getJavaClass());
    visitFile(file.getBaseLanguageRoot());
  }
}
