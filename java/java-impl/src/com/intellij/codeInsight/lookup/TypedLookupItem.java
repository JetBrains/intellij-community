/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface TypedLookupItem {
  @Nullable 
  PsiType getType();
}
