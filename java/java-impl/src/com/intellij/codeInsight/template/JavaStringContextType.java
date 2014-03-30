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
package com.intellij.codeInsight.template;

import com.intellij.lang.StdLanguages;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaStringContextType extends TemplateContextType {
  public JavaStringContextType() {
    super("JAVA_STRING", "String", JavaCodeContextType.Generic.class);
  }

  @Override
  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(StdLanguages.JAVA)) {
      return isStringLiteral(file.findElementAt(offset));
    }
    return false;
  }

  static boolean isStringLiteral(PsiElement element) {
    return element instanceof PsiJavaToken && ((PsiJavaToken) element).getTokenType() == JavaTokenType.STRING_LITERAL;
  }
}
