// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.impl.source.PackageAccessibilityStatementElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;


public class JavaPackageAccessibilityStatementElementType extends JavaStubElementType implements ICompositeElementType,
                                                                                              ParentProviderElementType {
  private final IElementType myParentElementType;

  public JavaPackageAccessibilityStatementElementType(@NotNull String debugName, @NotNull IElementType parentElementType) {
    super(debugName);
    myParentElementType = parentElementType;
  }


  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new PackageAccessibilityStatementElement(this);
  }

  @Override
  public @NotNull Set<IElementType> getParents() {
    return Collections.singleton( myParentElementType);
  }

  public static @NotNull PsiPackageAccessibilityStatement.Role typeToRole(@NotNull IElementType type) {
    if (type == JavaElementType.EXPORTS_STATEMENT) return PsiPackageAccessibilityStatement.Role.EXPORTS;
    if (type == JavaElementType.OPENS_STATEMENT) return PsiPackageAccessibilityStatement.Role.OPENS;
    throw new IllegalArgumentException("Unknown type: " + type);
  }
}