/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressForClassFix extends SuppressFix {
  public SuppressForClassFix(final HighlightDisplayKey key) {
    super(key);
  }

  public SuppressForClassFix(final String id) {
   super(id);
  }

  @Override
  @Nullable
  public PsiJavaDocumentedElement getContainer(final PsiElement element) {
    PsiJavaDocumentedElement container = super.getContainer(element);
    if (container == null || container instanceof PsiClass){
      return null;
    }
    while (container != null ) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if ((parentClass == null || container.getParent() instanceof PsiDeclarationStatement || container.getParent() instanceof PsiClass) &&
          container instanceof PsiClass && !(container instanceof PsiImplicitClass)){
        return container;
      }
      container = parentClass;
    }
    return null;
  }

  @Override
  @NotNull
  public String getText() {
    return getFamilyName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("suppress.inspection.class");
  }

  @Override
  public int getPriority() {
    return 50;
  }
}
