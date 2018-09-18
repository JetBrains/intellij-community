// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public interface SymbolDeclarationSearcher {

  ExtensionPointName<SymbolDeclarationSearcher> EP_NAME = ExtensionPointName.create("com.intellij.declarationSearcher");

  boolean processDeclarations(@NotNull PsiElement element, int offsetInElement, @NotNull Processor<? super SymbolDeclaration> processor);
}
