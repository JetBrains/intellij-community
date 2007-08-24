/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.reflect.CustomDomChildrenDescription;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class CustomDomChildrenDescriptionImpl extends AbstractDomChildDescriptionImpl implements CustomDomChildrenDescription, AbstractCollectionChildDescription {
  private final JavaMethod myGetter;
  private static final NotNullFunction<DomInvocationHandler,List<XmlTag>> CUSTOM_TAGS_GETTER = new NotNullFunction<DomInvocationHandler, List<XmlTag>>() {
    @NotNull
    public List<XmlTag> fun(final DomInvocationHandler handler) {
      return DomImplUtil.getCustomSubTags(handler.getXmlTag(), handler);
    }
  };

  public CustomDomChildrenDescriptionImpl(@NotNull final JavaMethod getter) {
    super(DomReflectionUtil.extractCollectionElementType(getter.getGenericReturnType()));
    myGetter = getter;
  }

  public JavaMethod getGetterMethod() {
    return myGetter;
  }

  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomInvocationHandler parent) {
    return parent.getCollectionChildren(this, CUSTOM_TAGS_GETTER);
  }

  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomElement parent) {
    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(parent);
    if (handler != null) return getValues(handler);
    return (List<? extends DomElement>)myGetter.invoke(parent, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public int compareTo(final AbstractDomChildDescriptionImpl o) {
    return equals(o) ? 0 : -1;
  }

  public List<XmlTag> getSubTags(final DomInvocationHandler handler) {
    return DomImplUtil.getCustomSubTags(handler.getXmlTag(), handler);
  }

  public EvaluatedXmlName createEvaluatedXmlName(final DomInvocationHandler parent, final XmlTag childTag) {
    return new DummyEvaluatedXmlName(childTag.getLocalName(), childTag.getNamespace());
  }
}
