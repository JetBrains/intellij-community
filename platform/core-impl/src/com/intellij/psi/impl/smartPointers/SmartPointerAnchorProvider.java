// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows SmartPointer that points to stubbed psi element to survive stub-to-AST switch
 *
 * @author Dennis.Ushakov
 */
public abstract class SmartPointerAnchorProvider {
  public static final ExtensionPointName<SmartPointerAnchorProvider> EP_NAME = ExtensionPointName.create("com.intellij.smartPointer.anchorProvider");

  /**
   * Provides anchor used for restoring elements after stub-to-AST switch.
   * One can use name identifier (such as tag or method name) as an anchor
   * @return anchor to be used when restoring element
   */
  public abstract @Nullable PsiElement getAnchor(@NotNull PsiElement element);

  /**
   * @return restored original element using anchor
   */
  public abstract @Nullable PsiElement restoreElement(@NotNull PsiElement anchor);
}
