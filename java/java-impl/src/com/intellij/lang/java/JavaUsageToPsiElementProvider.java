/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java;

import com.intellij.lang.Language;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.tree.java.ImportStatementElement;
import com.intellij.usages.UsageToPsiElementProvider;

/**
 * @author Konstantin Bulenkov
 */
public class JavaUsageToPsiElementProvider extends UsageToPsiElementProvider {
  private static final Language JAVA = Language.findLanguageByID("JAVA");
  private static final int MAX_HOPES = 17;

  @Override
  public PsiElement getAppropriateParentFrom(PsiElement element) {
    if (element.getLanguage() == JAVA) {
      int hopes = 0;
      while (hopes++ < MAX_HOPES && element != null) {
        if (element instanceof PsiField ||
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
