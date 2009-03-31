/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiTarget;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface PsiDeclaredTarget extends PsiTarget {

  /**
   * @return the range containing name. Range should be relative to {@link #getNavigationElement()} result
   */
  @Nullable  
  TextRange getNameIdentifierRange();

}
