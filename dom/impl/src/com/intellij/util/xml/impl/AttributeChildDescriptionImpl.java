/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class AttributeChildDescriptionImpl extends DomChildDescriptionImpl implements DomAttributeChildDescription {
  private final JavaMethod myGetterMethod;

  protected AttributeChildDescriptionImpl(final String attributeName, final JavaMethod getter) {
    super(attributeName, getter.getGenericReturnType());
    myGetterMethod = getter;
  }

  public DomNameStrategy getDomNameStrategy(DomElement parent) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(DomReflectionUtil.getRawType(getType()), true);
    return strategy == null ? parent.getNameStrategy() : strategy;
  }


  public final JavaMethod getGetterMethod() {
    return myGetterMethod;
  }

  @Nullable
  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return getGetterMethod().getAnnotation(annotationClass);
  }

  public List<? extends DomElement> getValues(DomElement parent) {
    return Arrays.asList(getDomAttributeValue(parent));
  }

  public String getCommonPresentableName(DomNameStrategy strategy) {
    throw new UnsupportedOperationException("Method getCommonPresentableName is not yet implemented in " + getClass().getName());
  }

  public GenericAttributeValue getDomAttributeValue(DomElement parent) {
    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(parent);
    if (handler != null) {
      return (GenericAttributeValue)handler.getAttributeChild(myGetterMethod.getSignature()).getProxy();
    }
    return (GenericAttributeValue)myGetterMethod.invoke(parent, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final AttributeChildDescriptionImpl that = (AttributeChildDescriptionImpl)o;

    if (myGetterMethod != null ? !myGetterMethod.equals(that.myGetterMethod) : that.myGetterMethod != null) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + (myGetterMethod != null ? myGetterMethod.hashCode() : 0);
    return result;
  }
}
