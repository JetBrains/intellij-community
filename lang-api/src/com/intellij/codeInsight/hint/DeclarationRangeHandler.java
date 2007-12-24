package com.intellij.codeInsight.hint;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.MixinEP;
import org.jetbrains.annotations.NotNull;

public interface DeclarationRangeHandler {
  ExtensionPointName<MixinEP<DeclarationRangeHandler>> EP_NAME = ExtensionPointName.create("com.intellij.declarationRangeHandler");
  
  @NotNull
  TextRange getDeclarationRange(@NotNull PsiElement container);
}
