/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 * @author ven
 */
public interface PsiNameValuePair extends PsiElement {
  PsiNameValuePair[] EMPTY_ARRAY = new PsiNameValuePair[0];

  PsiIdentifier getNameIdentifier ();
  String getName ();

  PsiAnnotationMemberValue getValue();
}
