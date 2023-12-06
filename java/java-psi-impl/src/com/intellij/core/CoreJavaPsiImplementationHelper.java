// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.javadoc.PsiSnippetAttributeValue;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class CoreJavaPsiImplementationHelper extends JavaPsiImplementationHelper {
  private final Project myProject;

  public CoreJavaPsiImplementationHelper(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public PsiClass getOriginalClass(@NotNull PsiClass psiClass) {
    return psiClass;
  }

  @NotNull
  @Override
  public PsiJavaModule getOriginalModule(@NotNull PsiJavaModule module) {
    return module;
  }

  @NotNull
  @Override
  public PsiElement getClsFileNavigationElement(@NotNull PsiJavaFile clsFile) {
    return clsFile;
  }

  @NotNull
  @Override
  public LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile) {
    return PsiUtil.getLanguageLevel(myProject);
  }

  @Override
  public ASTNode getDefaultImportAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override
  public PsiElement getDefaultMemberAnchor(@NotNull PsiClass psiClass, @NotNull PsiMember firstPsi) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override
  public void setupCatchBlock(@NotNull String exceptionName, @NotNull PsiType exceptionType, PsiElement context, @NotNull PsiCatchSection element) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override
  public @NotNull PsiSymbolReference getSnippetRegionSymbol(@NotNull PsiSnippetAttributeValue value) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }
}