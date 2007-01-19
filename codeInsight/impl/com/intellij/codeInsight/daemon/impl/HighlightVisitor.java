package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public interface HighlightVisitor {
  ExtensionPointName<HighlightVisitor> EP_HIGHLIGHT_VISITOR = new ExtensionPointName<HighlightVisitor>("com.intellij.highlightVisitor");

  boolean suitableForFile(PsiFile file);
  void visit(PsiElement element, HighlightInfoHolder holder);
  void setRefCountHolder(RefCountHolder refCountHolder);
  void init();
  HighlightVisitor clone();
}
