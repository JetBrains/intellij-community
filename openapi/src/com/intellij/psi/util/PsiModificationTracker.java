/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;

public interface PsiModificationTracker {
  Key MODIFICATION_COUNT = Key.create("MODIFICATION_COUNT");
  Key OUT_OF_CODE_BLOCK_MODIFICATION_COUNT = Key.create("OUT_OF_CODE_BLOCK_MODIFICATION_COUNT");

  long getModificationCount();
  long getOutOfCodeBlockModificationCount();

  boolean isInsideCodeBlock(PsiElement element);
}
