// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.converters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.converters.values.ClassValueConverter;
import org.jetbrains.annotations.NotNull;

public class ClassValueConverterImpl extends ClassValueConverter {
  private static final JavaClassReferenceProvider REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  static {
    REFERENCE_PROVIDER.setSoft(true);
    REFERENCE_PROVIDER.setAllowEmpty(true);
    REFERENCE_PROVIDER.setOption(JavaClassReferenceProvider.ALLOW_DOLLAR_NAMES, Boolean.TRUE);
  }

  @Override
  public PsiReference @NotNull [] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    return REFERENCE_PROVIDER.getReferencesByElement(element);
  }
}
