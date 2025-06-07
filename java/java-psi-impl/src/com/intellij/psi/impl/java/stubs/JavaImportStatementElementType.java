// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public abstract class JavaImportStatementElementType extends JavaStubElementType implements ICompositeElementType, ParentProviderElementType {
  @NotNull private final IElementType myParentElementType;

  public JavaImportStatementElementType(final @NonNls @NotNull String id, @NotNull IElementType parentElementType) {
    super(id);
    myParentElementType = parentElementType;
  }

  @Override
  public @NotNull Set<IElementType> getParents() {
    return Collections.singleton(myParentElementType);
  }
}
