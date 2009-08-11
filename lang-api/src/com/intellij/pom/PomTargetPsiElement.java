/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface PomTargetPsiElement extends PsiElement {

  @NotNull PomTarget getTarget();

}
