// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class TextResult implements Result{
  private final @NlsSafe String myText;

  public TextResult(@NotNull @NlsSafe String text) {
    myText = text;
  }

  public @NotNull @NlsSafe String getText() {
    return myText;
  }

  @Override
  public boolean equalsToText(String text, PsiElement context) {
    return text.equals(myText);
  }

  @Override
  public String toString() {
    return myText;
  }

  @Override
  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
  }
}