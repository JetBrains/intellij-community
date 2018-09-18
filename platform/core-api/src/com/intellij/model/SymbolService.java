// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.model.psi.PsiSymbol;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface SymbolService {

  @NotNull
  static SymbolService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SymbolService.class);
  }

  @NotNull
  static Symbol adaptPsiElement(@NotNull PsiElement element) {
    if (element instanceof Symbol) {
      return (Symbol)element;
    }
    else {
      return new PsiSymbol(element);
    }
  }

  @Nullable
  static PsiElement getPsiElement(@NotNull Symbol symbol) {
    if (symbol instanceof PsiElement) return (PsiElement)symbol;
    if (symbol instanceof PsiSymbol) return ((PsiSymbol)symbol).getElement();
    return null;
  }

  boolean processAllDeclarations(@NotNull PsiFile file, int offset, @NotNull Processor<? super SymbolDeclaration> processor);

  @NotNull
  Collection<? extends SymbolDeclaration> findAllDeclarations(@NotNull PsiFile file, int offset);

  @Nullable
  SymbolDeclaration findDeclarationAt(@NotNull PsiFile file, int offset);
}
