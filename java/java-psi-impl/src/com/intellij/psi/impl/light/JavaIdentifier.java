// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

public class JavaIdentifier extends LightIdentifier {
  private final PsiElement myElement;

  public JavaIdentifier(PsiManager manager, PsiElement element) {
    super(manager, element.getText());
    myElement = element;
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return myElement;
  }

  @Override
  public boolean isValid() {
    return myElement.isValid();
  }

  @Override
  public TextRange getTextRange() {
    return myElement.getTextRange();
  }

  @Override
  public PsiFile getContainingFile() {
    return myElement.getContainingFile();
  }

  @Override
  public int getStartOffsetInParent() {
    return myElement.getStartOffsetInParent();
  }

  @Override
  public int getTextOffset() {
    return myElement.getTextOffset();
  }

  @Override
  public PsiElement getParent() {
    return myElement.getParent();
  }

  @Override
  public PsiElement getPrevSibling() {
    return myElement.getPrevSibling();
  }

  @Override
  public PsiElement getNextSibling() {
    return myElement.getNextSibling();
  }

  @Override
  public PsiElement copy() {
    return new JavaIdentifier(myManager, myElement);
  }
}
