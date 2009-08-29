package com.intellij.codeInsight.quickfix;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;

public interface UnresolvedReferenceQuickFixProvider {
  ExtensionPointName<UnresolvedReferenceQuickFixProvider> EXTENSION_NAME =
      ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider");


  void registerFixes(PsiJavaCodeReferenceElement ref, QuickFixActionRegistrar registrar);
}
