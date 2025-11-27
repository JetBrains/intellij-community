// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public abstract class JavaClassReferenceListElementType extends JavaStubElementType implements ICompositeElementType {

  public JavaClassReferenceListElementType(@NotNull String id) {
    super(id, true);
  }

  public static @NotNull PsiReferenceList.Role elementTypeToRole(@NotNull IElementType type) {
    if (type == JavaStubElementTypes.EXTENDS_BOUND_LIST) return PsiReferenceList.Role.EXTENDS_BOUNDS_LIST;
    if (type == JavaStubElementTypes.EXTENDS_LIST) return PsiReferenceList.Role.EXTENDS_LIST;
    if (type == JavaStubElementTypes.IMPLEMENTS_LIST) return PsiReferenceList.Role.IMPLEMENTS_LIST;
    if (type == JavaStubElementTypes.THROWS_LIST) return PsiReferenceList.Role.THROWS_LIST;
    if (type == JavaStubElementTypes.PROVIDES_WITH_LIST) return PsiReferenceList.Role.PROVIDES_WITH_LIST;
    if (type == JavaStubElementTypes.PERMITS_LIST) return PsiReferenceList.Role.PERMITS_LIST;
    throw new RuntimeException("Unknown element type: " + type);
  }
}
