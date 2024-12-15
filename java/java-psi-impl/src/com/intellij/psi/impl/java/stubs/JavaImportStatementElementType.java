// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.impl.source.PsiImportModuleStatementImpl;
import com.intellij.psi.impl.source.PsiImportStatementImpl;
import com.intellij.psi.impl.source.PsiImportStaticStatementImpl;
import com.intellij.psi.impl.source.tree.java.ImportModuleStatementElement;
import com.intellij.psi.impl.source.tree.java.ImportStaticStatementElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public abstract class JavaImportStatementElementType extends JavaStubElementType implements ICompositeElementType, ParentProviderElementType, JavaNonCompositeElementType {
  @NotNull private final IElementType myParentElementType;

  public JavaImportStatementElementType(final @NonNls @NotNull String id, @NotNull IElementType parentElementType) {
    super(id);
    myParentElementType = parentElementType;
  }

  @Override
  public @NotNull Set<IElementType> getParents() {
    return Collections.singleton(myParentElementType);
  }

  @Override
  public PsiImportStatementBase createPsi(final @NotNull ASTNode node) {
    if (node instanceof ImportStaticStatementElement) {
      return new PsiImportStaticStatementImpl(node);
    }
    else if (node instanceof ImportModuleStatementElement) {
      return new PsiImportModuleStatementImpl(node);
    }
    else {
      return new PsiImportStatementImpl(node);
    }
  }
}
