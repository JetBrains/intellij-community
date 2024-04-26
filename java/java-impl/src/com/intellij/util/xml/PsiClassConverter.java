// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Dmitry Avdeev
 */
public class PsiClassConverter extends Converter<PsiClass> implements CustomReferenceConverter<PsiClass> {

  @Override
  public PsiClass fromString(final String s, final @NotNull ConvertContext context) {
    if (StringUtil.isEmptyOrSpaces(s)) return null;

    final DomElement element = context.getInvocationElement();
    final GlobalSearchScope scope = element instanceof GenericDomValue ? getScope(context) : null;
    return DomJavaUtil.findClass(s.trim(), context.getFile(), context.getModule(), scope);
  }

  @Override
  public @Nullable String getErrorMessage(final @Nullable String s, final @NotNull ConvertContext context) {
    return null;
  }

  @Override
  public String toString(final PsiClass t, final @NotNull ConvertContext context) {
    return t == null ? null : t.getQualifiedName();
  }

  @Override
  public PsiReference @NotNull [] createReferences(GenericDomValue<PsiClass> genericDomValue, PsiElement element, ConvertContext context) {

    ExtendClass extendClass = genericDomValue.getAnnotation(ExtendClass.class);
    final JavaClassReferenceProvider provider = createClassReferenceProvider(genericDomValue, context, extendClass);
    return provider.getReferencesByElement(element);
  }

  protected JavaClassReferenceProvider createClassReferenceProvider(final GenericDomValue<PsiClass> genericDomValue,
                                                                    final ConvertContext context,
                                                                    ExtendClass extendClass) {
    return createJavaClassReferenceProvider(genericDomValue, extendClass, new JavaClassReferenceProvider() {

      @Override
      public GlobalSearchScope getScope(Project project) {
        return PsiClassConverter.this.getScope(context);
      }
    });
  }

  public static JavaClassReferenceProvider createJavaClassReferenceProvider(final GenericDomValue genericDomValue,
                                                                            ExtendClass extendClass,
                                                                            final JavaClassReferenceProvider provider) {

    if (extendClass != null) {
      if (extendClass.value().length > 0) {
        provider.setOption(JavaClassReferenceProvider.SUPER_CLASSES, Arrays.asList(extendClass.value()));
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
      if (!extendClass.allowEnum()) {
        provider.setOption(JavaClassReferenceProvider.NOT_ENUM, Boolean.TRUE);
      }
      if (extendClass.jvmFormat()) {
        provider.setOption(JavaClassReferenceProvider.JVM_FORMAT, Boolean.TRUE);
      }
      provider.setAllowEmpty(extendClass.allowEmpty());
    }

    ClassTemplate template = genericDomValue.getAnnotation(ClassTemplate.class);
    if (template != null) {
      if (StringUtil.isNotEmpty(template.value())) {
        provider.setOption(JavaClassReferenceProvider.CLASS_TEMPLATE, template.value());
      }
      provider.setOption(JavaClassReferenceProvider.CLASS_KIND, template.kind());
    }

    provider.setSoft(true);
    return provider;
  }

  protected @Nullable GlobalSearchScope getScope(@NotNull ConvertContext context) {
    return context.getSearchScope();
  }

  public static class AnnotationType extends PsiClassConverter {

    @Override
    protected JavaClassReferenceProvider createClassReferenceProvider(GenericDomValue<PsiClass> genericDomValue,
                                                                      ConvertContext context,
                                                                      ExtendClass extendClass) {
      final JavaClassReferenceProvider provider = super.createClassReferenceProvider(genericDomValue, context,
                                                                                     extendClass);

      provider.setOption(JavaClassReferenceProvider.CLASS_KIND, ClassKind.ANNOTATION);
      return provider;
    }
  }

  public static class EnumType extends PsiClassConverter {

    @Override
    protected JavaClassReferenceProvider createClassReferenceProvider(GenericDomValue<PsiClass> genericDomValue,
                                                                      ConvertContext context,
                                                                      ExtendClass extendClass) {
      final JavaClassReferenceProvider provider = super.createClassReferenceProvider(genericDomValue, context,
                                                                                     extendClass);
      provider.setOption(JavaClassReferenceProvider.CLASS_KIND, ClassKind.ENUM);

      return provider;
    }
  }
}
