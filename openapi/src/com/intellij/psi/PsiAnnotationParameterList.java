/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public interface PsiAnnotationParameterList extends PsiElement {
  @NotNull
  PsiNameValuePair[] getAttributes();
}
