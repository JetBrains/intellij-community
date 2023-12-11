// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.ImportStatementElement;
import com.intellij.usages.UsageToPsiElementProvider;

/**
 * @author Konstantin Bulenkov
 */
public final class JavaUsageToPsiElementProvider extends UsageToPsiElementProvider {
  private static final Language JAVA = Language.findLanguageByID("JAVA");
  private static final int MAX_HOPES = 17;

  @Override
  public PsiElement getAppropriateParentFrom(PsiElement element) {
    if (element.getLanguage() == JAVA) {
      int hopes = 0;
      while (hopes++ < MAX_HOPES && element != null) {
        if (element instanceof PsiField ||
            element instanceof PsiClassInitializer ||
            element instanceof PsiMethod ||
            element instanceof ImportStatementElement ||
            element instanceof PsiClass
          ) return element;

        element = element.getParent();
      }
    }
    return null;
  }
}
