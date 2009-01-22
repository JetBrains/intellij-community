/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.semantic;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface SemElement {

  boolean isValid();

  @Nullable
  PsiElement getPsiElement();

  //@Nullable ?? getContainingFile() ??

}
