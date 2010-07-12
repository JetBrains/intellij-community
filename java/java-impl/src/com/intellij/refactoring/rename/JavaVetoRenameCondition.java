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
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.JspPsiUtil;

public class JavaVetoRenameCondition implements Condition<PsiElement> {
  public boolean value(final PsiElement element) {
    return element instanceof PsiJavaFile &&
           !JspPsiUtil.isInJspFile(element) &&
           !CollectHighlightsUtil.isOutsideSourceRoot((PsiFile)element) &&
           ((PsiJavaFile) element).getClasses().length > 0;
  }
}
