/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.pom.java.PomField;

public interface PsiField extends PsiMember, PsiVariable, PsiDocCommentOwner {
  PsiField[] EMPTY_ARRAY = new PsiField[0];
  PomField getPom();
}
