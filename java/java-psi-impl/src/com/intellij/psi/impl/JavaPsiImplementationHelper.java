// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @NotNull
  public abstract PsiClass getOriginalClass(@NotNull PsiClass psiClass);

  @NotNull
  public abstract PsiJavaModule getOriginalModule(@NotNull PsiJavaModule module);

  @NotNull
  public abstract PsiElement getClsFileNavigationElement(@NotNull PsiJavaFile clsFile);

  @NotNull
  public abstract LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile);

  @Nullable
  public abstract ASTNode getDefaultImportAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement);

  @Nullable
  public abstract PsiElement getDefaultMemberAnchor(@NotNull PsiClass psiClass, @NotNull PsiMember firstPsi);

  public abstract void setupCatchBlock(@NotNull String exceptionName,
                                       @NotNull PsiType exceptionType,
                                       @Nullable PsiElement context,
                                       @NotNull PsiCatchSection element);

  public abstract @NotNull PsiSymbolReference getSnippetRegionSymbol(@NotNull PsiSnippetAttributeValue value);

}