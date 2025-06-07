// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;

public abstract class RepositoryUnresolvedReferenceQuickFixProvider
  extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  private static @NotNull
  String getFQTypeName(@NotNull PsiJavaCodeReferenceElement ref) {
    while (ref.getParent() != null && ref.getParent() instanceof PsiJavaCodeReferenceElement) {
      ref = (PsiJavaCodeReferenceElement)ref.getParent();
    }
    String name = ref.getCanonicalText();
    PsiFile file = ref.getContainingFile();
    if (!(file instanceof PsiJavaFile javaFile)) {
      return name;
    }
    String suffix = "." + name;
    PsiImportList importList = javaFile.getImportList();
    if (importList != null) {
      for (PsiImportStatement importStatement : importList.getImportStatements()) {
        String qualifiedName = importStatement.getQualifiedName();
        if (qualifiedName != null && (qualifiedName.endsWith(suffix) || qualifiedName.equals(name))) {
          return qualifiedName;
        }
      }
    }
    return name;
  }

  protected abstract boolean isSuspectedName(@NotNull String fqTypeName);

  protected abstract
  @NotNull
  RepositoryLibraryDescription getLibraryDescription();

  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(ref);
    if (module != null && isSuspectedName(getFQTypeName(ref))) {
      registrar.register(new RepositoryAddLibraryAction(module, getLibraryDescription()));
    }
  }

  @Override
  public @NotNull Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}
