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
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    if (myGenericValue instanceof GenericAttributeValue) {
      return new TextRange(1, ((GenericAttributeValue)myGenericValue).getXmlAttributeValue().getTextLength() - 1);
    }
    final XmlTag tag = myGenericValue.getXmlTag();
    assert tag != null;
    XmlTagValue tagValue = tag.getValue();
    final String text = tagValue.getText();
    final String trimmed = text.trim();
    final int index = text.indexOf(trimmed);
    final int startOffset = tagValue.getTextRange().getStartOffset() - tag.getTextRange().getStartOffset() + index;
    return new TextRange(startOffset, startOffset + trimmed.length());
  }

  protected final GenericDomValue<T> getGenericValue() {
    return myGenericValue;
  }

  public boolean isSoft() {
    return true;
  }

  @Nullable
  protected PsiElement resolveInner(T o) {
    final Converter<T> converter = getConverter();
    if (converter instanceof ResolvingConverter) {
      return ((ResolvingConverter<T>)converter).resolve(o, getConvertContext());
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

  public boolean isReferenceTo(final PsiElement element) {
    final Converter<T> converter = getConverter();
    if (converter instanceof ResolvingConverter) {
      return ((ResolvingConverter<T>)converter).isReferenceTo(element, getStringValue(), myGenericValue.getValue(), getConvertContext());
    }
    return super.isReferenceTo(element);
  }

  private String getStringValue() {
    return myGenericValue.getStringValue();
  }

  private Converter<T> getConverter() {
    return myGenericValue.getConverter();
  }

  @Nullable
  public PsiElement resolve() {
    final T value = myGenericValue.getValue();
    return value == null ? null : resolveInner(value);
  }

  public String getCanonicalText() {
    String value = getStringValue();
    return value != null ? value : J2EEBundle.message("unknown.j2ee.reference.canonical.text");
  }

  public String getUnresolvedMessagePattern() {
    return getConverter().getErrorMessage(getStringValue(), getConvertContext());
  }

  private ConvertContextImpl getConvertContext() {
    return new ConvertContextImpl(DomManagerImpl.getDomInvocationHandler(myGenericValue));
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    final Converter<T> converter = getConverter();
    if (converter instanceof ResolvingConverter) {
      ((ResolvingConverter)converter).bindReference(myGenericValue, getConvertContext(), element);
      return myGenericValue.getXmlTag();
    }

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
    final Converter<T> converter = getConverter();
    if (converter instanceof ResolvingConverter) {
      final ResolvingConverter<T> resolvingConverter = (ResolvingConverter<T>)converter;
      final ConvertContext convertContext = getConvertContext();
      ArrayList<Object> result = new ArrayList<Object>();
      for (T variant: resolvingConverter.getVariants(convertContext)) {
        String name = converter.toString(variant, convertContext);
        if (name != null) {
          result.add(LookupValueFactory.createLookupValue(name, ElementPresentationManager.getIcon(variant)));
        }
      }
      for (final String string : resolvingConverter.getAdditionalVariants()) {
        result.add(LookupValueFactory.createLookupValue(string, null));
      }
      return result.toArray();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
