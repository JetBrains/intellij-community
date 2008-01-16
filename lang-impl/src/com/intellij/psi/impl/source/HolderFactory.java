/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public interface HolderFactory {
  DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context);
  DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, boolean validity);
  DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context);
  DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table);
  DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context, CharTable table);
  DummyHolder createHolder(@NotNull PsiManager manager, final CharTable table, final Language language);
  
}