package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface HighlightRangeExtension {
  ExtensionPointName<HighlightRangeExtension> EP_NAME = ExtensionPointName.create("com.intellij.highlightRangeExtension");

  boolean isForceHighlightParents(PsiFile file);
}
