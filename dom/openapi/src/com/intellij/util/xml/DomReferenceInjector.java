package com.intellij.util.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DomReferenceInjector {
  @Nullable String resolveString(@Nullable String unresolvedText, @NotNull ConvertContext context);

  @NotNull PsiReference[] inject(@Nullable String unresolvedText, @NotNull PsiElement element, @NotNull ConvertContext context);
}
