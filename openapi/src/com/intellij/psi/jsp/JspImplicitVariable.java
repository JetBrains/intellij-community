/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.jsp;

import com.intellij.psi.ImplicitVariable;
import com.intellij.psi.PsiElement;

public interface JspImplicitVariable extends JspElement, ImplicitVariable{
  JspImplicitVariable[] EMPTY_ARRAY = new JspImplicitVariable[0];
  int INSIDE = 1;
  int AFTER = 2;
  int getDeclarationRange();

  PsiElement getDeclaration();
}