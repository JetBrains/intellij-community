/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;

/**
 * Represents a package. 
 *  @author dsl
 */
public class PackageWrapper {
  private final PsiManager myManager;
  private final String myQualifiedName;

  public PackageWrapper(PsiManager manager, String qualifiedName) {
    myManager = manager;
    myQualifiedName = qualifiedName;
  }

  public PackageWrapper(PsiPackage aPackage) {
    myManager = aPackage.getManager();
    myQualifiedName = aPackage.getQualifiedName();
  }

  public PsiManager getManager() { return myManager; }

  public PsiDirectory[] getDirectories() {
    final PsiPackage aPackage = myManager.findPackage(myQualifiedName);
    if (aPackage != null) {
      return aPackage.getDirectories();
    } else {
      return PsiDirectory.EMPTY_ARRAY;
    }
  }

  public boolean exists() {
    return myManager.findPackage(myQualifiedName) != null;
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  public boolean equalToPackage(PsiPackage aPackage) {
    return aPackage != null && myQualifiedName.equals(aPackage.getQualifiedName());
  }

  public static PackageWrapper create(PsiPackage aPackage) {
    return new PackageWrapper(aPackage);
  }
}
