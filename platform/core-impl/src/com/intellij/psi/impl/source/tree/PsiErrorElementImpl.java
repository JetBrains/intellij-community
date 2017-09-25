
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

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NotNull;

public class PsiErrorElementImpl extends CompositePsiElement implements PsiErrorElement{
  private final String myErrorDescription;

  public PsiErrorElementImpl(String errorDescription) {
    super(TokenType.ERROR_ELEMENT);
    myErrorDescription = errorDescription;
  }

  @Override
  public String getErrorDescription() {
    return myErrorDescription;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitErrorElement(this);
  }

  public String toString(){
    return "PsiErrorElement:" + getErrorDescription();
  }

  @Override
  @NotNull
  public Language getLanguage() {
    PsiElement master = this;
    while (true) {
      master = master.getNextSibling();
      if (master == null || master instanceof OuterLanguageElement) {
        PsiElement parent = getParent();
        return parent == null ? Language.ANY : parent.getLanguage();
      }
      if (master instanceof PsiWhiteSpace || master instanceof PsiErrorElement) continue;
      return master.getLanguage();
    }
  }
}