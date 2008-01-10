/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.Nullable;

public interface TreeGenerator {
  @Nullable
  TreeElement generateTreeFor(PsiElement original, CharTable table, final PsiManager manager);
}