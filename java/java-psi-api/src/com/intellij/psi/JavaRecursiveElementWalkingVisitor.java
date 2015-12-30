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

import org.jetbrains.annotations.NotNull;

/**
 * a JavaElementVisitor which also visits all children elements
 * in a tree pre-order, see <a href="https://en.wikipedia.org/wiki/Tree_traversal#Pre-order">Tree traversal:Pre-order</a> for details.
 * <p>
 * <b>Note</b>: This visitor handles all containing elements without consuming stack space, so it can be used even for very deep trees.
 * <b>Note 2</b>: This visitor works for source-based PSI only. Any elements implementing {@link PsiCompiledElement} will be rejected.
 */
public abstract class JavaRecursiveElementWalkingVisitor extends JavaElementVisitor {
  private final PsiWalkingState myWalkingState = new PsiWalkingState(this){
    @Override
    public void elementFinished(@NotNull PsiElement element) {
      JavaRecursiveElementWalkingVisitor.this.elementFinished(element);
    }
  };

  @Override
  public void visitElement(PsiElement element) {
    myWalkingState.elementStarted(element);
  }

  protected void elementFinished(@NotNull PsiElement element) {
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitExpression(expression);
    myWalkingState.startedWalking(); // do not traverse from scratch
    visitReferenceElement(expression);
  }

  public void stopWalking() {
    myWalkingState.stopWalking();
  }
}
