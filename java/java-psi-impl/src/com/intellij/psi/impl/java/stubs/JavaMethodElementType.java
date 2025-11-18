// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

@ApiStatus.Internal
public abstract class JavaMethodElementType extends JavaStubElementType implements ICompositeElementType {

  JavaMethodElementType(final @NonNls String name) {
    super(name);
  }
}
