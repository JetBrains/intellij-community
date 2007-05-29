/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class GenericDomValueReference<T> extends PsiReferenceBase<XmlElement> implements EmptyResolveMessageProvider {
  private final GenericDomValue<T> myGenericValue;

  public GenericDomValueReference(GenericDomValue<T> domValue) {
    super(DomUtil.getValueElement(domValue));
    myGenericValue = domValue.createStableCopy();
    assert domValue.getXmlTag() != null;
    setRangeInElement(createTextRange());
  }

  protected final PsiManager getPsiManager() {
    return PsiManager.getInstance(myGenericValue.getManager().getProject());
  }

  protected TextRange createTextRange() {
    if (myGenericValue instanceof GenericAttributeValue) {
      final GenericAttributeValue genericAttributeValue = (GenericAttributeValue)myGenericValue;
      final XmlAttributeValue attributeValue = genericAttributeValue.getXmlAttributeValue();
      if (attributeValue == null) {
        return TextRange.from(0, genericAttributeValue.getXmlAttribute().getTextLength());
      }

      final int length = attributeValue.getTextLength();
      return length < 2 ? TextRange.from(0, length) : new TextRange(1, length - 1);
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

  protected Converter<T> getConverter() {
    return WrappingConverter.getDeepestConverter(myGenericValue.getConverter(), myGenericValue);
  }

  @Nullable
  public PsiElement resolve() {
    final T value = myGenericValue.getValue();
    return value == null ? null : resolveInner(value);
  }

  public String getCanonicalText() {
    return getStringValue();
  }

  public String getUnresolvedMessagePattern() {
    final ConvertContextImpl context = getConvertContext();
    return getConverter().getErrorMessage(getStringValue(), context);
  }

  private ConvertContextImpl getConvertContext() {
    return new ConvertContextImpl(DomManagerImpl.getDomInvocationHandler(myGenericValue));
  }

  public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
    final Converter<T> converter = getConverter();
    if (converter instanceof ResolvingConverter) {
      ((ResolvingConverter)converter).handleElementRename(myGenericValue, getConvertContext(), newElementName);
      return myGenericValue.getXmlTag();
    }
    return super.handleElementRename(newElementName);
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
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
      ArrayList<Object> result = new ArrayList<Object>();
      final ConvertContext convertContext = getConvertContext();
      for (T variant: resolvingConverter.getVariants(convertContext)) {
        String name = converter.toString(variant, convertContext);
        if (name != null) {
          final Icon presentationIcon = ElementPresentationManager.getIcon(variant);
          final Icon icon = presentationIcon == null && variant instanceof Iconable? ((Iconable)variant).getIcon(Iconable.ICON_FLAG_READ_STATUS) : presentationIcon;
          result.add(LookupValueFactory.createLookupValue(name, icon));
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
