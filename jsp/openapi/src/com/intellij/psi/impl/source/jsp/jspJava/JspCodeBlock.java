/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;

/**
 * @author peter
 */
public interface JspCodeBlock extends PsiCodeBlock {
  PsiElement[] getLocalDeclarations();
}
