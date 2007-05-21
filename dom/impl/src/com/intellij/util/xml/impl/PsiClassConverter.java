/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class PsiClassConverter extends Converter<PsiClass> implements CustomReferenceConverter<PsiClass> {

  public PsiClass fromString(final String s, final ConvertContext context) {
    return s == null ? null:context.findClass(s, null);
  }

  @Nullable
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return null;
  }

  public String toString(final PsiClass t, final ConvertContext context) {
    return t == null ? null:t.getQualifiedName();
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<PsiClass> genericDomValue, PsiElement element, ConvertContext context) {

    ExtendClass extendClass = genericDomValue.getAnnotation(ExtendClass.class);
    final JavaClassReferenceProvider provider = new JavaClassReferenceProvider(getScope(genericDomValue));
    if (extendClass != null) {
      if (!StringUtil.isEmpty(extendClass.value())) {
        provider.setOption(JavaClassReferenceProvider.EXTEND_CLASS_NAMES, new String[] {extendClass.value()});
      }
      if (extendClass.instantiatable()) {
        provider.setOption(JavaClassReferenceProvider.INSTANTIATABLE, Boolean.TRUE);
      }
      if (!extendClass.allowAbstract()) {
        provider.setOption(JavaClassReferenceProvider.CONCRETE, Boolean.TRUE);
      }
      if (!extendClass.allowInterface()) {
        provider.setOption(JavaClassReferenceProvider.NOT_INTERFACE, Boolean.TRUE);
      }
      provider.setAllowEmpty(extendClass.allowEmpty());

    }
    provider.setSoft(true);
    return provider.getReferencesByElement(element);
  }

  @Nullable
  private static GlobalSearchScope getScope(final GenericDomValue domValue) {
    final Module module = domValue.getModule();
    return module == null ? null : GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
  }

}
