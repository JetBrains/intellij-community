// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.source;

import com.intellij.lang.LanguageExtension;
import com.intellij.lang.jvm.JvmElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * This {@link #EP extension} defines {@link PsiElement}{@code -> [}{@link JvmElement}{@code ]} mapping
 */
public interface JvmDeclarationSearcher {

  LanguageExtension<JvmDeclarationSearcher> EP = new LanguageExtension<>("com.intellij.jvm.declarationSearcher");

  @NotNull
  Collection<JvmElement> findDeclarations(@NotNull PsiElement declaringElement);
  
  default @Nullable PsiElement adjustIdentifierElement(@NotNull PsiElement identifierElement) {
    return null;
  }
}
