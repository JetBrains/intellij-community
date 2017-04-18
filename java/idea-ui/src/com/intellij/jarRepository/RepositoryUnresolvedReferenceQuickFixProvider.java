/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  static private
  @NotNull
  String getFQTypeName(@NotNull PsiJavaCodeReferenceElement ref) {
    while (ref.getParent() != null && ref.getParent() instanceof PsiJavaCodeReferenceElement) {
      ref = (PsiJavaCodeReferenceElement)ref.getParent();
    }
    String name = ref.getCanonicalText();
    PsiFile file = ref.getContainingFile();
    if (!(file instanceof PsiJavaFile)) {
      return name;
    }
    String suffix = "." + name;
    PsiJavaFile javaFile = (PsiJavaFile)file;
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

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}
