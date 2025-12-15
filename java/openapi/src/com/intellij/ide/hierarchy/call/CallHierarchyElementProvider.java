// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.call;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface CallHierarchyElementProvider {
  ExtensionPointName<CallHierarchyElementProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.hierarchy.elementProvider");

  boolean canProvide(@NotNull PsiMember element);

  Collection<PsiElement> provideReferencedMembers(@NotNull PsiMember reference);

  void appendReferencedMethods(@NotNull PsiMethod methodToFind, @NotNull JavaCallHierarchyData hierarchyData);
}
