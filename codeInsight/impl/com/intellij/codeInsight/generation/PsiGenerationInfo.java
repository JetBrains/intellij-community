/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiGenerationInfo<T extends PsiMember> implements GenerationInfo {
  private T myMethod;

  public PsiGenerationInfo(@NotNull final T method) {
    myMethod = method;
  }

  @NotNull
  public final T getPsiMember() {
    return myMethod;
  }

  public void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
    PsiElement newMember = GenerateMembersUtil.insert(aClass, myMethod, anchor, before);
    myMethod = (T)CodeStyleManager.getInstance(aClass.getProject()).shortenClassReferences(newMember);
  }
}
