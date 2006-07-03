/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.javaee.J2EEBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class GenericDomValueReference<T> extends PsiReferenceBase<XmlElement> implements EmptyResolveMessageProvider {
  private final GenericDomValue<T> myGenericValue;

  public GenericDomValueReference(GenericDomValue<T> domValue) {
    super(DomUtil.getValueElement(domValue));
    myGenericValue = domValue;
    assert domValue.getXmlTag() != null;
    setRangeInElement(createTextRange());
  }

  protected final PsiManager getPsiManager() {
    return PsiManager.getInstance(myGenericValue.getManager().getProject());
  }

  protected TextRange createTextRange() {
    return DomUtil.getValueRange(myGenericValue);
  }

  protected final GenericDomValue<T> getGenericValue() {
    return myGenericValue;
  }

  protected PsiElement resolveInner(T o) {
    final Converter<T> converter = myGenericValue.getConverter();
    if (converter instanceof ResolvingConverter) {
      final PsiElement psiElement = ((ResolvingConverter<T>)converter).getPsiElement(o);
      return psiElement == null && o != null ? getElement() : psiElement;
    }

    if (o instanceof PsiElement) {
      return (PsiElement)o;
    }
    if (o instanceof DomElement) {
      return ((DomElement)o).getXmlElement();
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
    return o != null ? getElement() : null;
  }

  @Nullable
  public PsiElement resolve() {
    final T value = myGenericValue.getValue();
    return value == null ? null : resolveInner(value);
  }

  public String getCanonicalText() {
    String value = myGenericValue.getStringValue();
    return value != null ? value : J2EEBundle.message("unknown.j2ee.reference.canonical.text");
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
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
