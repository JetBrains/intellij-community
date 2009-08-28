/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.pom.PomTarget;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface PsiTarget extends PomTarget {
  @NotNull
  PsiElement getNavigationElement();
}
