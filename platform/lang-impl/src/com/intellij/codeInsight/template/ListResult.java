// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.List;

public class ListResult implements Result {
  private final List<Result> myComponents;

  public ListResult(List<Result> components) {
    myComponents = components;
  }

  public List<Result> getComponents() {
    return myComponents;
  }

  @Override
  public String toString() {
    return myComponents.toString();
  }

  @Override
  public boolean equalsToText(String text, PsiElement context) {
    return false;
  }

  @Override
  public void handleFocused(PsiFile psiFile, Document document, int segmentStart, int segmentEnd) {
  }
}
