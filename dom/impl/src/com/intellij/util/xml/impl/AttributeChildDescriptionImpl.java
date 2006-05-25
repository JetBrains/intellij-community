/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class AttributeChildDescriptionImpl extends DomChildDescriptionImpl implements DomAttributeChildDescription {
  private final JavaMethodSignature myGetterMethod;
  private final Required myRequired;
  private Class<?> myDeclaringClass;

  protected AttributeChildDescriptionImpl(final String attributeName, final Method getter, Required required) {
    super(attributeName, getter.getGenericReturnType());
    myDeclaringClass = getter.getDeclaringClass();
    myGetterMethod = JavaMethodSignature.getSignature(getter);
    myRequired = required;
  }

  public DomNameStrategy getDomNameStrategy(DomElement parent) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(DomUtil.getRawType(getType()), true);
    return strategy == null ? parent.getNameStrategy() : strategy;
  }


  public final JavaMethodSignature getGetterMethod() {
    return myGetterMethod;
  }

  @Nullable
  public final <T extends Annotation> T getAnnotation(Class<? extends T> annotationClass) {
    return getGetterMethod().findAnnotation(annotationClass, myDeclaringClass);
  }

  public List<? extends DomElement> getValues(DomElement parent) {
    return Arrays.asList(getDomAttributeValue(parent));
  }

  public String getCommonPresentableName(DomNameStrategy strategy) {
    throw new UnsupportedOperationException("Method getCommonPresentableName is not yet implemented in " + getClass().getName());
  }

  public GenericAttributeValue getDomAttributeValue(DomElement parent) {
    try {
      return (GenericAttributeValue)DomManagerImpl.getDomInvocationHandler(ModelMergerUtil.getImplementation(parent, DomElement.class)).doInvoke(myGetterMethod);
    }
    catch (ProcessCanceledException e) {
      throw e; 
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
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
