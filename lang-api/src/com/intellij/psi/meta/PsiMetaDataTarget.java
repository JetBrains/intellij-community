/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.meta;

import com.intellij.pom.PomRenameableTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTarget;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiMetaDataTarget extends PsiTarget<PsiElement> implements PomRenameableTarget {
  private PsiMetaData myMetaData;

  public PsiMetaDataTarget(@NotNull PsiMetaData metaData) {
    super(metaData.getDeclaration());
    myMetaData = metaData;
  }

  @NotNull
  public String getTargetName() {
    return myMetaData.getName();
  }

  public boolean isWritable() {
    return myMetaData instanceof PsiWritableMetaData && getDeclaringElement().isWritable();
  }

  public void setTargetName(@NotNull String newName) {
    ((PsiWritableMetaData) myMetaData).setName(newName);
  }

}
