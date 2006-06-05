/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.javaee.web.PsiReferenceConverter;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.XmlReference;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class GenericValueReferenceProvider implements PsiReferenceProvider {

  private final Map<Class, PsiReferenceFactory> myProviders = new HashMap<Class, PsiReferenceFactory>();

  public void addReferenceProviderForClass(Class clazz, PsiReferenceFactory provider) {
    myProviders.put(clazz, provider);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement psiElement) {
    if (!(psiElement instanceof XmlTag || psiElement instanceof XmlAttributeValue)) return GenericReference.EMPTY_ARRAY;
    PsiElement originalElement = psiElement.getUserData(PsiUtil.ORIGINAL_KEY);
    if (originalElement != null) {
      psiElement = originalElement;
    }

    final XmlTag tag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);

    final DomElement domElement = DomManager.getDomManager(psiElement.getManager().getProject()).getDomElement(tag);
    if (domElement == null) return GenericReference.EMPTY_ARRAY;

    PsiReference[] reference = PsiReference.EMPTY_ARRAY;


    if (psiElement instanceof XmlAttributeValue) {
      final XmlAttributeValue value = (XmlAttributeValue)psiElement;
      final PsiElement parent = value.getParent();
      if (parent instanceof XmlAttribute) {
        final String name = ((XmlAttribute)parent).getLocalName();
        final DomAttributeChildDescription childDescription = domElement.getGenericInfo().getAttributeChildDescription(name);
        if (childDescription != null) {
          reference = createReference(childDescription.getValues(domElement).get(0));
        }
      }
    }
    else {
      reference = createReference(domElement);
    }

    DomElement parent = (domElement instanceof GenericDomValue) ? domElement.getParent() : domElement;
    GenericDomValue nameElement = parent.getGenericInfo().getNameDomElement(parent);
    if (nameElement != null && nameElement.getValue() instanceof String) {
      final XmlElement valueElement = DomUtil.getValueElement(nameElement);
      if (valueElement == psiElement || nameElement.getXmlTag() == psiElement) {
        PsiReference selfReference = XmlReference.createSelfReference((XmlElement)psiElement, valueElement);
//      return new PsiReference[] {selfReference};
        reference = ArrayUtil.append(reference, selfReference);
      }
    }

    return reference;
  }

  @NotNull
  private PsiReference[] createReference(DomElement element) {
    if (!(element instanceof GenericDomValue)) return PsiReference.EMPTY_ARRAY;

    PsiElement psiElement;
    if (element instanceof GenericAttributeValue) {
      psiElement = ((GenericAttributeValue)element).getXmlAttributeValue();
      if (psiElement == null) return PsiReference.EMPTY_ARRAY;
    }
    else {
      if (element.getXmlTag().getValue().getTextElements().length == 0) return PsiReference.EMPTY_ARRAY;
      psiElement = element.getXmlElement();
    }

    Converter converter = ((GenericDomValue)element).getConverter();
    if (converter instanceof PsiReferenceConverter) {
      return ((PsiReferenceConverter)converter).createReferences(psiElement, false);
    }

    GenericDomValue domElement = (GenericDomValue)element;
    final Class clazz = DomUtil.getGenericValueParameter(domElement.getDomElementType());
    if (PsiType.class.isAssignableFrom(clazz)) {
      return new PsiReference[]{new PsiTypeReference(this, (GenericDomValue<PsiType>)domElement)};
    }
    if (PsiClass.class.isAssignableFrom(clazz)) {
      JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
      return provider.getReferencesByElement(psiElement);
//      return new PsiReference[] {new PsiClassReference(this, (GenericDomValue<PsiClass>)domElement)};
    }
    if (Integer.class.isAssignableFrom(clazz)) {
      return new PsiReference[]{new GenericDomValueReference(this, domElement) {
        public Object[] getVariants() {
          return new Object[]{"239", "42"};
        }
      }};
    }
    if (String.class.isAssignableFrom(clazz)) {
      return PsiReference.EMPTY_ARRAY;
    }
    PsiReferenceFactory provider = myProviders.get(clazz);
    if (provider != null) {
      return provider.getReferencesByElement(psiElement);
    }

    return new PsiReference[]{new GenericDomValueReference(this, domElement)};
  }


  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
