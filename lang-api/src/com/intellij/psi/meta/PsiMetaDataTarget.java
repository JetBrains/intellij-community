/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.meta;

import com.intellij.pom.PomRenameableTarget;
import com.intellij.psi.DelegatePsiTarget;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiMetaDataTarget extends DelegatePsiTarget implements PomRenameableTarget<PsiMetaDataTarget> {
  private final PsiMetaData myMetaData;

  public PsiMetaDataTarget(@NotNull PsiMetaData metaData) {
    super(metaData.getDeclaration());
    myMetaData = metaData;
  }

  @NotNull
  public String getName() {
    return myMetaData.getName();
  }

  public boolean isWritable() {
    return myMetaData instanceof PsiWritableMetaData && getNavigationElement().isWritable();
  }

  public PsiMetaDataTarget setName(@NotNull String newName) {
    ((PsiWritableMetaData) myMetaData).setName(newName);
    return this;
  }

}
