// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.symbol;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Root element for the symbol-based language structure view. Makes transition from psi to symbol. Psi file may have no symbol by itself, so
 * root element is psi based and children are symbol based.
 */
public class FileSymbolTreeElement extends PsiTreeElementBase<PsiFile> {
  private final @NotNull SymbolBasedStructureViewModel myStructureViewModel;

  public FileSymbolTreeElement(@NotNull PsiFile psiFile, @NotNull SymbolBasedStructureViewModel structureViewModel) {
    super(psiFile);
    myStructureViewModel = structureViewModel;
  }

  @Override
  public @Nullable String getPresentableText() {
    var element = getElement();
    return element == null ? "invalid" : element.getName();
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    var element = getElement();
    return element == null ? Collections.emptyList() : myStructureViewModel.collectClosestChildrenSymbols(element);
  }
}
