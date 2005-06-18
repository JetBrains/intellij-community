/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public interface PsiFile extends PsiElement, PsiFileSystemItem {
  PsiFile[] EMPTY_ARRAY = new PsiFile[0];

  @Nullable VirtualFile getVirtualFile();

  @Nullable PsiDirectory getContainingDirectory();

  long getModificationStamp();
  void setModificationStamp(long modificationStamp);

  @NotNull PsiElement[] getOnDemandImports(boolean includeImplicit, boolean checkIncludes);
  @NotNull PsiClass[] getSingleClassImports(boolean checkIncludes);
  @NotNull String[] getImplicitlyImportedPackages();
  @NotNull PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences();
  @Nullable PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass);

  @Nullable PsiFile getOriginalFile();

  boolean canContainJavaCode();

  @NotNull FileType getFileType();

  @NotNull PsiFile[] getPsiRoots();

  @NotNull PsiFile createPseudoPhysicalCopy();
}
