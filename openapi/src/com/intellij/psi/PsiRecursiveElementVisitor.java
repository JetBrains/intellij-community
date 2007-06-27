/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.psi.jsp.JspFile;
import com.intellij.util.containers.Stack;


/**
 * Represents a PSI element visitor which recursively visits the children of the element
 * on which the visit was started. 
 */
public abstract class PsiRecursiveElementVisitor extends PsiElementVisitor {
  // This stack thing is intended to prevent exponential child traversing due to visitReferenceExpression calls both visitRefElement
  // and visitExpression.
  private final Stack<PsiReferenceExpression> myRefExprsInVisit = new Stack<PsiReferenceExpression>();

  public void visitElement(PsiElement element) {
    if (myRefExprsInVisit.size() > 0 && myRefExprsInVisit.peek() == element) {
      myRefExprsInVisit.pop();
      myRefExprsInVisit.push(null);
    }
    else {
      element.acceptChildren(this);
    }
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
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
  public void visitJspFile(JspFile file) {
    super.visitJspFile(file);
    visitClass(file.getJavaClass());
    visitFile(file.getBaseLanguageRoot());
  }
}
