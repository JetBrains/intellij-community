// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface PsiSymbolReferenceProvider {

  @NotNull
  Collection<? extends PsiSymbolReference> getReferences(@NotNull PsiExternalReferenceHost element, @NotNull PsiSymbolReferenceHints hints);
}
