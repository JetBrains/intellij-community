/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionStub;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import org.jetbrains.annotations.NotNull;

public class PsiClassOrFunctionalExpressionListCellRenderer extends PsiElementListCellRenderer<NavigatablePsiElement> {
  @Override
  public String getElementText(NavigatablePsiElement element) {
    return element instanceof PsiClass ? ClassPresentationUtil.getNameForClass((PsiClass)element, false) 
                                       : renderFunctionalExpression((PsiFunctionalExpression)element);
  }

  @Override
  protected String getContainerText(NavigatablePsiElement element, final String name) {
    return PsiClassListCellRenderer.getContainerTextStatic(element);
  }

  @Override
  protected int getIconFlags() {
    return 0;
  }

  @NotNull
  static String renderFunctionalExpression(@NotNull PsiFunctionalExpression expression) {
    final StubElement stub = ((StubBasedPsiElementBase<?>)expression).getGreenStub();
    return stub instanceof FunctionalExpressionStub
                       ? ((FunctionalExpressionStub)stub).getPresentableText()
                       : PsiExpressionTrimRenderer.render(expression);
  }
}
