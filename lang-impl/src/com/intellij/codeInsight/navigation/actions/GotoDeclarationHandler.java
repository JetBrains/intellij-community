package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface GotoDeclarationHandler {
  ExtensionPointName<GotoDeclarationHandler> EP_NAME = ExtensionPointName.create("com.intellij.gotoDeclarationHandler");

  @Nullable
  PsiElement getGotoDeclarationTarget(PsiElement sourceElement);
}
