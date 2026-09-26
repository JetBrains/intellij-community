// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spi.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.spi.parsing.SPIElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class SPIClassProvidersElementList extends ASTWrapperPsiElement {
  public SPIClassProvidersElementList(@NotNull ASTNode node) {
    super(node);
  }
  
  public @Unmodifiable List<SPIClassProviderReferenceElement> getElements() {
    return findChildrenByType(SPIElementTypes.PROVIDER);
  }
}
