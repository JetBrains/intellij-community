package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiErrorElement;

/**
 * @author yole
 */
public interface ErrorQuickFixProvider {
  ExtensionPointName<ErrorQuickFixProvider> EP_NAME = ExtensionPointName.create("com.intellij.errorQuickFixProvider");
  
  void registerErrorQuickFix(PsiErrorElement errorElement, HighlightInfo highlightInfo);
}
