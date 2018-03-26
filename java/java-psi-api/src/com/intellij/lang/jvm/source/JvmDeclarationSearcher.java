// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.source;

import com.intellij.lang.LanguageExtension;
import com.intellij.lang.jvm.JvmElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * This {@link #EP extension} defines {@link PsiElement}{@code -> [}{@link JvmElement}{@code ]} mapping
 */
public interface JvmDeclarationSearcher {

  LanguageExtension<JvmDeclarationSearcher> EP = new LanguageExtension<>("com.intellij.jvm.declarationSearcher");

  /**
   * For needs of line markers and inspections elements are searched by identifier PsiElement.
   * In this case it's up to implementation to define relation between identifier and declaring elements.
   * <p>
   * {@link #isDeclaringElement Default} implementation considers {@link PsiNameIdentifierOwner} and
   * its {@link PsiNameIdentifierOwner#getNameIdentifier getNameIdentifier()}.
   */
  default void findDeclarationsByIdentifier(@NotNull PsiElement identifierElement, @NotNull Consumer<? super JvmElement> consumer) {
    PsiElement declaringElement = findDeclaringElement(identifierElement);
    if (declaringElement == null) return;
    findDeclarations(declaringElement, consumer);
  }

  @Nullable
  default PsiElement findDeclaringElement(@NotNull PsiElement identifierElement) {
    final PsiElement parent = identifierElement.getParent();
    return isDeclaringElement(identifierElement, parent) ? parent : null;
  }

  default boolean isDeclaringElement(@NotNull PsiElement identifierElement, @NotNull PsiElement declaringElement) {
    return declaringElement instanceof PsiNameIdentifierOwner &&
           ((PsiNameIdentifierOwner)declaringElement).getNameIdentifier() == identifierElement;
  }

  /**
   * Feeds to the consumer the {@link JvmElement}s which would be found in the class file
   * after compilation of the {@code declaringElement}.
   */
  void findDeclarations(@NotNull PsiElement declaringElement, @NotNull Consumer<? super JvmElement> consumer);
}
