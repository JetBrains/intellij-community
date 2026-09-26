// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class JavaImportStatementElementType extends JavaStubElementType implements ICompositeElementType {

  public JavaImportStatementElementType(final @NonNls @NotNull String id) {
    super(id);
  }
}
