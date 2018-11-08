// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public class JavaFocusModeProvider implements FocusModeProvider {
  @Nullable
  @Override
  public Segment calcFocusRange(int primaryCaretOffset, PsiFile file) {
    NavigatablePsiElement parent = PsiTreeUtil
      .getParentOfType(file.findElementAt(primaryCaretOffset), PsiMethod.class, PsiClass.class, PsiClassInitializer.class, PsiFile.class);
    return parent == null ? null : parent.getTextRange();
  }
}
