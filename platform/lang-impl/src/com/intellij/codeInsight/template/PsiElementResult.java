// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;

public class PsiElementResult implements Result {
  private SmartPsiElementPointer myPointer = null;

  public PsiElementResult(PsiElement element) {
    if (element != null) {
      myPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    }
  }

  public PsiElement getElement() {
    return myPointer != null ? myPointer.getElement() : null;
  }

  @Override
  public boolean equalsToText(String text, PsiElement context) {
    return text.equals(toString());
  }

  @Override
  public String toString() {
    PsiElement element = getElement();
    if (element != null) {
      return element.getText();
    }
    return null;
  }

  @Override
  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
  }
}