package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public interface HighlightRangeExtension {
  ExtensionPointName<HighlightRangeExtension> EP_NAME = ExtensionPointName.create("com.intellij.highlightRangeExtension");

  @Nullable
  List<PsiElement> getElementsToHighlight(PsiElement root, PsiElement commonParent, int startOffset, int endOffset);

  boolean isForceHighlightParents(PsiFile file);
}
