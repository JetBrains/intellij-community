// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.java.TypeParameterListElement;
import com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.annotations.NotNull;

public class JavaTypeParameterListElementType extends JavaStubElementType implements ICompositeElementType {
  public JavaTypeParameterListElementType() {
    super("TYPE_PARAMETER_LIST", true);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new TypeParameterListElement();
  }
}