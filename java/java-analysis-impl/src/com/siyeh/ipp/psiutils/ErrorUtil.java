/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public final class ErrorUtil {

  private ErrorUtil() {
  }

  /**
   * Checks only immediate children. No expensive full tree traversal.
   *
   * @return true, if an PsiErrorElement was found, false otherwise.
   */
  public static boolean containsError(PsiElement element) {
    // check only immediate children, full tree traversal is too expensive
    return PsiUtilCore.hasErrorElementChild(element);
  }

  /**
   * Does full tree traversal check for PsiErrorElements.
   *
   * @return true, if an PsiErrorElement was found, false otherwise.
   */
  public static boolean containsDeepError(PsiElement element) {
    final ErrorElementVisitor visitor = new ErrorElementVisitor();
    element.accept(visitor);
    return visitor.containsErrorElement();
  }

  private static class ErrorElementVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean containsErrorElement = false;

    @Override
    public void visitErrorElement(@NotNull PsiErrorElement element) {
      containsErrorElement = true;
      stopWalking();
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression literal) {
      if (PsiLiteralUtil.isUnsafeLiteral(literal)) {
        containsErrorElement = true;
        stopWalking();
      }
    }

    public boolean containsErrorElement() {
      return containsErrorElement;
    }
  }
}