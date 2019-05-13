/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class JavaArrayInitializerUnwrapper extends JavaUnwrapper {

  public JavaArrayInitializerUnwrapper() {
    super(CodeInsightBundle.message("unwrap.array.initializer"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    if (e instanceof PsiArrayInitializerExpression) {
      final PsiElement gParent = e.getParent();
      if (gParent instanceof PsiNewExpression && gParent.getParent() instanceof PsiVariable) {
        return true;
      }
    }

    return false;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)element;
    final PsiElement newExpression = arrayInitializerExpression.getParent();
    context.extractElement(arrayInitializerExpression, newExpression);
    context.deleteExactly(newExpression);
  }
}
