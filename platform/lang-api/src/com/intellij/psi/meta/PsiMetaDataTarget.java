// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.meta;

import com.intellij.pom.PomRenameableTarget;
import com.intellij.psi.DelegatePsiTarget;
import org.jetbrains.annotations.NotNull;

public class PsiMetaDataTarget extends DelegatePsiTarget implements PomRenameableTarget<PsiMetaDataTarget> {
  private final PsiMetaData myMetaData;

  public PsiMetaDataTarget(@NotNull PsiMetaData metaData) {
    super(metaData.getDeclaration());
    myMetaData = metaData;
  }

  @Override
  public @NotNull String getName() {
    return myMetaData.getName();
  }

  @Override
  public boolean isWritable() {
    return myMetaData instanceof PsiWritableMetaData && getNavigationElement().isWritable();
  }

  @Override
  public PsiMetaDataTarget setName(@NotNull String newName) {
    ((PsiWritableMetaData) myMetaData).setName(newName);
    return this;
  }

}
