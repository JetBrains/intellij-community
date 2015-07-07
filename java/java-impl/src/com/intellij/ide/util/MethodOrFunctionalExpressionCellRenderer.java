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
package com.intellij.ide.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

import javax.swing.*;

public class MethodOrFunctionalExpressionCellRenderer extends PsiElementListCellRenderer<NavigatablePsiElement> {
  private final PsiClassListCellRenderer myClassListCellRenderer = new PsiClassListCellRenderer();
  private final MethodCellRenderer myMethodCellRenderer;
  
  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames) {
    this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
  }
  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
    myMethodCellRenderer = new MethodCellRenderer(showMethodNames, options);
  }

  public String getElementText(NavigatablePsiElement element) {
    return element instanceof PsiMethod ? myMethodCellRenderer.getElementText((PsiMethod)element) 
                                        : PsiExpressionTrimRenderer.render((PsiExpression)element);
  }

  protected Icon getIcon(PsiElement element) {
    return element instanceof PsiMethod ? myMethodCellRenderer.getIcon(element) : super.getIcon(element);
  }

  public String getContainerText(final NavigatablePsiElement element, final String name) {
    return element instanceof PsiMethod ? myMethodCellRenderer.getContainerText((PsiMethod)element, name) 
                                        : PsiClassListCellRenderer.getContainerTextStatic(element);
  }

  public int getIconFlags() {
    return myClassListCellRenderer.getIconFlags();
  }
}
