/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.psi.PsiFile;

public interface PackageSet {
  boolean contains(PsiFile file, NamedScopesHolder holder);
  PackageSet createCopy();
  String getText();
  int getNodePriority();
}