package com.intellij.ide.projectView.impl;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;

import javax.swing.tree.TreeNode;

public interface PsiRetriever {
  @Nullable
  PsiElement getPsiElement(@Nullable TreeNode node);
}
