// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomRenameableTarget;
import org.jetbrains.annotations.NotNull;

public class RenameableDelegatePsiTarget extends DelegatePsiTarget implements PomRenameableTarget<RenameableDelegatePsiTarget>{
  public RenameableDelegatePsiTarget(@NotNull PsiNamedElement element) {
    super(element);
  }

  @Override
  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  @Override
  public RenameableDelegatePsiTarget setName(@NotNull String newName) {
    ((PsiNamedElement)getNavigationElement()).setName(newName);
    return this;
  }

  @Override
  public @NotNull String getName() {
    return StringUtil.notNullize(((PsiNamedElement)getNavigationElement()).getName());
  }
}
