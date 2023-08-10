// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LanguageStructureViewBuilder extends LanguageExtension<PsiStructureViewFactory>{
  public static final LanguageStructureViewBuilder INSTANCE = new LanguageStructureViewBuilder();

  private LanguageStructureViewBuilder() {
    super(PsiStructureViewFactory.EP_NAME);
  }

  public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile file) {
    PsiStructureViewFactory factory = forLanguage(file.getLanguage());
    if (factory != null) {
      return factory.getStructureViewBuilder(file);
    }
    return null;
  }
}