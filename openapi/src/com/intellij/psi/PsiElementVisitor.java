/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.aspects.psi.gen.PsiAspectElementVisitor;
/**
 *
 */
public abstract class PsiElementVisitor extends PsiAspectElementVisitor {
  public void visitCodeFragment(PsiCodeFragment codeFragment) {
    visitFile(codeFragment);
  }

}
