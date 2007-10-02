/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Can be implemented by {@link com.intellij.util.xml.Converter} instance, or used with
 * {@link @com.intellij.util.xml.Referencing} annotation.
 *
 * @author peter
 */
public interface CustomReferenceConverter<T> {

  /**
   * Will be called on creating {@link com.intellij.psi.PsiReference}s for {@link com.intellij.util.xml.GenericDomValue}
   * Returned {@link com.intellij.psi.PsiReference}s should be soft ({@link com.intellij.psi.PsiReference#isSoft()} should return <code>true</code>).
   * To highlight unresolved references, create a {@link com.intellij.util.xml.highlighting.DomElementsInspection} and register it.
   *
   * @param value GenericDomValue in question
   * @param element corresponding PSI element
   * @param context {@link com.intellij.util.xml.ConvertContext}
   * @return custom {@link com.intellij.psi.PsiReference}s for the value
   */
  @NotNull
  PsiReference[] createReferences(GenericDomValue<T> value, PsiElement element, ConvertContext context);
}
