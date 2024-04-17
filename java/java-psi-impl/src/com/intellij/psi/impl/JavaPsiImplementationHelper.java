// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
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

}