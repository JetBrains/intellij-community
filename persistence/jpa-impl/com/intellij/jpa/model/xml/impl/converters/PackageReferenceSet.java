/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.jpa.model.xml.impl.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class PackageReferenceSet extends ReferenceSetBase<PsiPackageReference> {

  private final GenericDomValue<PsiPackage> myGenericDomValue;
  private final ConvertContext myContext;

  public PackageReferenceSet(@NotNull final PsiElement element, final int offset, final @NotNull GenericDomValue<PsiPackage> genericDomValue,
                             final ConvertContext context) {
    super(element, offset);
    myGenericDomValue = genericDomValue;
    myContext = context;
  }

  @NotNull
  protected PsiPackageReference createReference(final TextRange range, final int index) {
    return new PsiPackageReference(this, range, index);
  }

  public GenericDomValue<PsiPackage> getGenericDomValue() {
    return myGenericDomValue;
  }

  public ConvertContext getContext() {
    return myContext;
  }

}