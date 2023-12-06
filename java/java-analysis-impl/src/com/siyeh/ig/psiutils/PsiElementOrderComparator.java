// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiElement;

import java.util.Comparator;

public final class PsiElementOrderComparator implements Comparator<PsiElement> {

  private static final PsiElementOrderComparator INSTANCE = new PsiElementOrderComparator();

  private PsiElementOrderComparator() {}

  @Override
  public int compare(PsiElement element1, PsiElement element2) {
    final int offset1 = element1.getTextOffset();
    final int offset2 = element2.getTextOffset();
    return offset1 - offset2;
  }

  public static PsiElementOrderComparator getInstance() {
    return INSTANCE;
  }
}