/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

import com.intellij.psi.PsiFile;

public interface PsiNonJavaFileReferenceProcessor {
  boolean process(PsiFile file, int startOffset, int endOffset);
}
