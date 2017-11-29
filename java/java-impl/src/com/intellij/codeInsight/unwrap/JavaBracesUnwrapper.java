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
package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class JavaBracesUnwrapper extends JavaUnwrapper {
  public JavaBracesUnwrapper() {
    super(CodeInsightBundle.message("unwrap.braces"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof PsiBlockStatement && !belongsToControlStructures(e);
  }

  private boolean belongsToControlStructures(PsiElement e) {
    PsiElement p = e.getParent();

    return p instanceof PsiIfStatement
           || p instanceof PsiLoopStatement
           || p instanceof PsiTryStatement
           || p instanceof PsiCatchSection;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    context.extractFromBlockOrSingleStatement((PsiStatement)element, element);
    context.delete(element);
  }
}