/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;

public interface PsiFile extends PsiElement, PsiNamedElement{
  PsiFile[] EMPTY_ARRAY = new PsiFile[0];

  VirtualFile getVirtualFile();

  void checkSetName(String name) throws IncorrectOperationException;

  PsiDirectory getContainingDirectory();

  long getModificationStamp();
  void setModificationStamp(long modificationStamp);

  PsiElement[] getOnDemandImports(boolean includeImplicit, boolean checkIncludes);
  PsiClass[] getSingleClassImports(boolean checkIncludes);
  String[] getImplicitlyImportedPackages();
  PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences();
  PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass);

  PsiFile getOriginalFile();
  
  boolean canContainJavaCode();

  FileType getFileType();

  PsiFile[] getPsiRoots();

  PsiFile createPseudoPhysicalCopy();
}
