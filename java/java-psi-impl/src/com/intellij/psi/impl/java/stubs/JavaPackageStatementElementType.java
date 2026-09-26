// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.java.PackageStatementElement;
import com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.annotations.NotNull;


public class JavaPackageStatementElementType extends JavaStubElementType implements ICompositeElementType {
  public JavaPackageStatementElementType() {
    super("PACKAGE_STATEMENT");
  }


  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new PackageStatementElement();
  }
}