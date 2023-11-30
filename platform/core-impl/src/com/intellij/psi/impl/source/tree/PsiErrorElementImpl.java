
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NotNull;

public class PsiErrorElementImpl extends CompositePsiElement implements PsiErrorElement{
  private final @NlsContexts.DetailedDescription String myErrorDescription;

  public PsiErrorElementImpl(@NotNull @NlsContexts.DetailedDescription String errorDescription) {
    super(TokenType.ERROR_ELEMENT);
    myErrorDescription = errorDescription;
  }

  @Override
  public @NotNull String getErrorDescription() {
    return myErrorDescription;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitErrorElement(this);
  }

  @Override
  public String toString(){
    return "PsiErrorElement:" + getErrorDescription();
  }

  @Override
  public @NotNull Language getLanguage() {
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