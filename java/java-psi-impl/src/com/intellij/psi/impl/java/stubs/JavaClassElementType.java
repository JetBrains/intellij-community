// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public abstract class JavaClassElementType extends JavaStubElementType implements ParentProviderElementType, ICompositeElementType {
  private final IElementType myParentElementType;

  public JavaClassElementType(@NotNull String id, @NotNull IElementType type) {
    super(id);
    myParentElementType = type;
  }

  @Override
  public @NotNull Set<IElementType> getParents() {
    return Collections.singleton(myParentElementType);
  }
}