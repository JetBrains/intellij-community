// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.impl.source.PackageAccessibilityStatementElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;


public class JavaPackageAccessibilityStatementElementType extends JavaStubElementType implements ICompositeElementType {
  public JavaPackageAccessibilityStatementElementType(@NotNull String debugName) {
    super(debugName);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new PackageAccessibilityStatementElement(this);
  }


  public static @NotNull PsiPackageAccessibilityStatement.Role typeToRole(@NotNull IElementType type) {
    if (type == JavaElementType.EXPORTS_STATEMENT) return PsiPackageAccessibilityStatement.Role.EXPORTS;
    if (type == JavaElementType.OPENS_STATEMENT) return PsiPackageAccessibilityStatement.Role.OPENS;
    throw new IllegalArgumentException("Unknown type: " + type);
  }
}