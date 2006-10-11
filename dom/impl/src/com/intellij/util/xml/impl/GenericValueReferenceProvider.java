/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.javaee.web.PsiReferenceConverter;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class GenericValueReferenceProvider implements PsiReferenceProvider {

  private final Map<Class, PsiReferenceFactory> myProviders = new HashMap<Class, PsiReferenceFactory>();

  public void addReferenceProviderForClass(Class clazz, PsiReferenceFactory provider) {
    myProviders.put(clazz, provider);
  }

  @NotNull
  public final PsiReference[] getReferencesByElement(PsiElement psiElement) {
    if (!(psiElement instanceof XmlTag || psiElement instanceof XmlAttributeValue)) {
      return PsiReference.EMPTY_ARRAY;
    }

    PsiElement originalElement = psiElement.getUserData(PsiUtil.ORIGINAL_KEY);
    if (originalElement != null) {
      psiElement = originalElement;
    }

    final XmlTag tag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);

    DomElement domElement = DomManager.getDomManager(psiElement.getManager().getProject()).getDomElement(tag);
    if (domElement == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    if (psiElement instanceof XmlAttributeValue) {
      final PsiElement parent = psiElement.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttribute attribute = (XmlAttribute)parent;
        final String name = attribute.getLocalName();
        final DomAttributeChildDescription childDescription = domElement.getGenericInfo().getAttributeChildDescription(name);
        if (childDescription != null) {
          domElement = childDescription.getDomAttributeValue(domElement);
        }
      }
    }

    if (!(domElement instanceof GenericDomValue)) {
      return PsiReference.EMPTY_ARRAY;
    }

    GenericDomValue domValue = (GenericDomValue)domElement;

    final Convert annotation = domValue.getAnnotation(Convert.class);
    boolean soft = annotation != null && annotation.soft();
    final Converter converter = domValue.getConverter();
    if (converter instanceof ResolvingConverter) {
      final Set additionalVariants = ((ResolvingConverter)converter).getAdditionalVariants();
      if (additionalVariants.contains(domValue.getStringValue())) {
        soft = true;
      }
    }


    PsiReference[] references = createReferences(domValue, (XmlElement)psiElement, soft, converter);

    // creating "declaration" reference
    DomElement parent = domElement.getParent();
    if (references.length == 0) {
      final NameValue nameValue = domElement.getAnnotation(NameValue.class);
      if (nameValue != null && nameValue.referencable()) {
        references = ArrayUtil.append(references, PsiReferenceBase.createSelfReference(psiElement, parent.getXmlElement()), PsiReference.class);
      }
    }
    return references;
  }

  private static <T extends Annotation> DomInvocationHandler getInvocationHandler(final GenericDomValue domValue) {
    return DomManagerImpl.getDomInvocationHandler(domValue);
  }

  @NotNull
  protected final PsiReference[] createReferences(GenericDomValue domValue, XmlElement psiElement, final boolean soft, final Converter converter) {
    if (converter instanceof PsiReferenceConverter) {
      return ((PsiReferenceConverter)converter).createReferences(psiElement, soft);
    }
    final boolean isResolvingConverter = converter instanceof ResolvingConverter;

    final DomInvocationHandler invocationHandler = getInvocationHandler(domValue);
    final Class clazz = DomUtil.getGenericValueParameter(invocationHandler.getDomElementType());
    if (clazz == null) return PsiReference.EMPTY_ARRAY;

    if (ReflectionCache.isAssignable(PsiType.class, clazz)) {
      return new PsiReference[]{new PsiTypeReference((GenericDomValue<PsiType>)domValue)};
    }
    if (ReflectionCache.isAssignable(PsiClass.class, clazz)) {
      ExtendClass extendClass = invocationHandler.getAnnotation(ExtendClass.class);
      JavaClassReferenceProvider provider;
      if (extendClass == null) {
        provider = new JavaClassReferenceProvider();
      }
      else {
        provider = new JavaClassReferenceProvider(extendClass.value(), extendClass.instantiatable());
      }
      provider.setSoft(soft || isResolvingConverter);
      final PsiReference[] references = provider.getReferencesByElement(psiElement);
      return isResolvingConverter ? ArrayUtil.append(references, new GenericDomValueReference(domValue, soft), PsiReference.class)
        : references;
    }
    if (!isResolvingConverter && ReflectionCache.isAssignable(Integer.class, clazz)) {
      return new PsiReference[]{new GenericDomValueReference<Integer>((GenericDomValue<Integer>)domValue, true) {
        public Object[] getVariants() {
          return new Object[]{"0"};
        }
      }};
    }
    if (!isResolvingConverter && ReflectionCache.isAssignable(String.class, clazz)) {
      return PsiReference.EMPTY_ARRAY;
    }
    PsiReferenceFactory provider = myProviders.get(clazz);
    if (provider != null) {
      return provider.getReferencesByElement(psiElement);
    }

    return new PsiReference[]{new GenericDomValueReference(domValue, soft)};
  }


  @NotNull
  public final PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public final PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
