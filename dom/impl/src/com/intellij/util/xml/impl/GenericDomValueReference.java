/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.javaee.J2EEBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class GenericDomValueReference<T> extends GenericReference {
  private final GenericDomValue<T> myGenericValue;
  private final XmlTag myXmlTag;
  private final TextRange myTextRange;
  private final XmlElement myValueElement;

  public GenericDomValueReference(final PsiReferenceProvider provider, GenericDomValue<T> domValue) {
    super(provider);
    myGenericValue = domValue;
    myXmlTag = domValue.getXmlTag();
    assert myXmlTag != null;
    myValueElement = DomUtil.getValueElement(domValue);
    assert myValueElement != null;
    myTextRange = createTextRange();
  }

  protected final PsiManager getPsiManager() {
    return PsiManager.getInstance(myGenericValue.getManager().getProject());
  }

  protected final XmlElement getValueElement() {
    return myValueElement;
  }

  protected TextRange createTextRange() {
    return DomUtil.getValueRange(myGenericValue);
  }

  protected final GenericDomValue<T> getGenericValue() {
    return myGenericValue;
  }

  public XmlElement getContext() {
    return myXmlTag;
  }

  public PsiReference getContextReference() {
    return null;
  }

  public ReferenceType getType() {
    return new ReferenceType(ReferenceType.UNKNOWN);
  }

  protected PsiElement resolveInner(T o) {
    if (o instanceof PsiElement) {
      return (PsiElement)o;
    }
    if (o instanceof DomElement) {
      final XmlElement element = ((DomElement)o).getGenericInfo().getNameElement((DomElement)o);
      return element != null? element : ((DomElement)o).getXmlElement();
    }
    if (o instanceof MergedObject) {
      final List<T> list = ((MergedObject<T>)o).getImplementations();
      for (final T o1 : list) {
        final PsiElement psiElement = resolveInner(o1);
        if (psiElement != null) {
          return psiElement;
        }
      }
    }
    if (o != null) {
      return getValueElement();
    }
    else {
      return null;
    }
  }

  public final PsiElement resolveInner() {
    final T value = myGenericValue.getValue();
    return value == null ? null : resolveInner(value);
  }

  public ReferenceType getSoftenType() {
    return getType();
  }

  public boolean needToCheckAccessibility() {
    return false;
  }

  public XmlElement getElement() {
    return myValueElement;
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  public String getCanonicalText() {
    String value = myGenericValue.getStringValue();
    if (value != null) {
      return value;
    }
    return J2EEBundle.message("unknown.j2ee.reference.canonical.text");
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    myGenericValue.setStringValue(newElementName);
    return myGenericValue.getXmlTag();
  }

  public String getUnresolvedMessagePattern() {
    return myGenericValue.getConverter().getErrorMessage(myGenericValue.getStringValue(), createConvertContext());
  }

  private ConvertContextImpl createConvertContext() {
    return new ConvertContextImpl(DomManagerImpl.getDomInvocationHandler(myGenericValue));
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof XmlTag) {
      DomElement domElement = myGenericValue.getManager().getDomElement((XmlTag) element);
      if (domElement != null) {
        myGenericValue.setValue((T)domElement);
      } else {
        myGenericValue.setStringValue(((XmlTag)element).getName());
      }
      return myGenericValue.getXmlTag();
    }
    return null;
  }

  public Object[] getVariants() {
    final Converter<T> converter = myGenericValue.getConverter();
    if (converter instanceof ResolvingConverter) {
      final ResolvingConverter<T> resolvingConverter = (ResolvingConverter<T>)converter;
      final ConvertContext convertContext = createConvertContext();
      final Collection<T> variants = resolvingConverter.getVariants(convertContext);
      ArrayList<Object> result = new ArrayList<Object>(variants.size());
      for (T variant: variants) {
        String name = converter.toString(variant, convertContext);
        if (name != null) {
          Icon icon = ElementPresentationManager.getIcon(variant);
          Object value = LookupValueFactory.createLookupValue(name, icon);
          result.add(value);
        }
      }
      return result.toArray();
    }
    return super.getVariants();
  }
}
