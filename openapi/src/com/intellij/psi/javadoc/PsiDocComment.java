/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;

public interface PsiDocComment extends PsiComment {
  PsiElement[] getDescriptionElements();
  PsiDocTag[] getTags();

  PsiDocTag findTagByName(String name);
  PsiDocTag[] findTagsByName(String name);
}