/*
 * @author max
 */
package com.intellij.codeInsight.daemon;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface ChangeLocalityDetector {
  @Nullable
  PsiElement getChangeHighlightingDirtyScopeFor(PsiElement changedElement);
}