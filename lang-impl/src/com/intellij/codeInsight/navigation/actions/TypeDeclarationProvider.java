package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface TypeDeclarationProvider {
  ExtensionPointName<TypeDeclarationProvider> EP_NAME = ExtensionPointName.create("com.intellij.typeDeclarationProvider");

  @Nullable
  PsiElement getSymbolType(PsiElement symbol);
}
