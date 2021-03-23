// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class JavaChopRecordComponentsAction extends AbstractJavaChopListAction<PsiRecordHeader, PsiRecordComponent> {
  @Override
  @Nullable("When failed") PsiRecordHeader extractList(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiRecordHeader.class, false, PsiCodeBlock.class, PsiExpression.class);
  }

  @Override
  @Nullable("When failed") List<PsiRecordComponent> getElements(@NotNull PsiRecordHeader list) {
    return Arrays.asList(list.getRecordComponents());
  }

  @Override
  boolean needTailBreak(@NotNull PsiRecordComponent last) {
    return true;
  }

  @Override
  boolean needHeadBreak(@NotNull PsiRecordComponent first) {
    return true;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.put.record.components.on.separate.lines");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
