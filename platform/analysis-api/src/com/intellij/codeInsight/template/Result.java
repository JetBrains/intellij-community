// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;

public interface Result {
  boolean equalsToText (@NonNls String text, PsiElement context);

  String toString();

  void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd);
}

