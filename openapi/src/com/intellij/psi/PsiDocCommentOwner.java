/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.javadoc.PsiDocComment;

public interface PsiDocCommentOwner extends PsiMember {
  PsiDocComment getDocComment();
  boolean isDeprecated();
}
