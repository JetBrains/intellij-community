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

public class JavaTryUnwrapper extends JavaUnwrapper {
  public JavaTryUnwrapper() {
    super(CodeInsightBundle.message("unwrap.try"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof PsiTryStatement;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PsiTryStatement trySt = (PsiTryStatement)element;

    PsiResourceList resourceList = trySt.getResourceList();
    if (resourceList != null) {
      for (PsiResourceListElement listElement : resourceList) {
        if (listElement instanceof PsiResourceVariable) {
          context.extractElement(listElement, trySt);
          if (context.myIsEffective) {
            PsiStatement emptyStatement = JavaPsiFacade.getElementFactory(resourceList.getProject()).createStatementFromText(";", trySt);
            trySt.getParent().addBefore(emptyStatement, trySt);
          }
        }
      }
    }
    context.extractFromCodeBlock(trySt.getTryBlock(), trySt);
    context.extractFromCodeBlock(trySt.getFinallyBlock(), trySt);

    context.delete(trySt);
  }
}
