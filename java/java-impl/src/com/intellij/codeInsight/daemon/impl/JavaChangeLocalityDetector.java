/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public class JavaChangeLocalityDetector implements ChangeLocalityDetector {
  @Nullable
  public PsiElement getChangeHighlightingDirtyScopeFor(final PsiElement element) {
    PsiElement parent = element.getParent();
    if (element instanceof PsiCodeBlock && parent instanceof PsiMethod && !((PsiMethod)parent).isConstructor() &&
        parent.getParent()instanceof PsiClass && !(parent.getParent()instanceof PsiAnonymousClass)) {
      // do not use this optimization for constructors and class initializers - to update non-initialized fields
      return parent;
    }
    return null;
  }
}