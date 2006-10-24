/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Can be implemented by {@link com.intellij.util.xml.Converter} instance
 *
 * @author peter
 */
public interface CustomReferenceConverter<T> {

  /**
   * Will be called on creating {@link com.intellij.psi.PsiReference}s for {@link com.intellij.util.xml.GenericDomValue}
   * @param value GenericDomValue in question
   * @param element corresponding PSI element
   * @param context {@link com.intellij.util.xml.ConvertContext}
   * @return custom {@link com.intellij.psi.PsiReference}s for the value
   */
  @NotNull
  PsiReference[] createReferences(GenericDomValue<T> value, PsiElement element, ConvertContext context);
}
