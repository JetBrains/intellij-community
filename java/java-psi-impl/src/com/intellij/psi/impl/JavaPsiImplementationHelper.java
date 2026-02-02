// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiType;
import com.intellij.psi.javadoc.PsiDocFragmentName;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiSnippetAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class JavaPsiImplementationHelper {
  public static JavaPsiImplementationHelper getInstance(@NotNull Project project) {
    return project.getService(JavaPsiImplementationHelper.class);
  }

  public abstract @NotNull PsiClass getOriginalClass(@NotNull PsiClass psiClass);

  public abstract @NotNull PsiJavaModule getOriginalModule(@NotNull PsiJavaModule module);

  public abstract @NotNull PsiElement getClsFileNavigationElement(@NotNull PsiJavaFile clsFile);

  public abstract @NotNull LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile);

  public abstract @Nullable ASTNode getDefaultImportAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement);

  public abstract @Nullable PsiElement getDefaultMemberAnchor(@NotNull PsiClass psiClass, @NotNull PsiMember firstPsi);

  public abstract void setupCatchBlock(@NotNull String exceptionName,
                                       @NotNull PsiType exceptionType,
                                       @Nullable PsiElement context,
                                       @NotNull PsiCatchSection element);

  public abstract @NotNull PsiSymbolReference getSnippetRegionSymbol(@NotNull PsiSnippetAttributeValue value);

  /**
   * Returns a PsiSymbolReference corresponding to the {@code @inheritDoc} token.
   * This makes it possible to navigate to the inherited doc.
   */
  public abstract @NotNull PsiSymbolReference getInheritDocSymbol(@NotNull PsiDocToken token);

  /**
   * Returns a PsiSymbolReference mapping a fragment reference ({@code ##fragment-id}) to its source ({@code <p id="fragment-id">…</p>}).
   */
  public abstract @Nullable PsiSymbolReference getFragmentNameSymbol(@NotNull PsiDocFragmentName token);
}