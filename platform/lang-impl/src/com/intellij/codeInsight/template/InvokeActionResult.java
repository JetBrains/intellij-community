
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public final class InvokeActionResult implements Result{
  private final Runnable myAction;

  public InvokeActionResult(Runnable action) {
    myAction = action;
  }

  public Runnable getAction() {
    return myAction;
  }

  @Override
  public boolean equalsToText(String text, PsiElement context) {
    return true; //no text result will be provided anyway
  }

  @Override
  public String toString() {
    return "";
  }

  @Override
  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    myAction.run();
  }
}