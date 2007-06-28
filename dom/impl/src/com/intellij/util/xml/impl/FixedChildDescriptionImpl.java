/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class FixedChildDescriptionImpl extends DomChildDescriptionImpl implements DomFixedChildDescription {
  private final JavaMethod[] myGetterMethods;
  private final int myCount;

  public FixedChildDescriptionImpl(final XmlName tagName, final Type type, final int count, final JavaMethod[] getterMethods) {
    super(tagName, type);
    assert getterMethods.length == count;
    myCount = count;
    myGetterMethods = getterMethods;
  }

  public JavaMethod getGetterMethod(int index) {
    return myGetterMethods[index];
  }

  public void initConcreteClass(final DomElement parent, final Class<? extends DomElement> aClass) {
    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(parent);
    assert handler != null;
    handler.setFixedChildClass(handler.createEvaluatedXmlName(getXmlName()), aClass);
  }

  @Nullable
  public <T extends Annotation> T getAnnotation(int index, Class<? extends T> annotationClass) {
    final T annotation = getGetterMethod(index).getAnnotation(annotationClass);
    if (annotation != null) return annotation;
    
    final Type elemType = getType();
    return elemType instanceof AnnotatedElement ? ((AnnotatedElement)elemType).getAnnotation(annotationClass) : null;
  }

  public int getCount() {
    return myCount;
  }

  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomElement element) {
    final ArrayList<DomElement> result = new ArrayList<DomElement>();
    for (JavaMethod method : myGetterMethods) {
      if (method != null) {
        result.add((DomElement) method.invoke(element, ArrayUtil.EMPTY_OBJECT_ARRAY));
      }
    }
    return result;
  }

  @NotNull
  public String getCommonPresentableName(@NotNull DomNameStrategy strategy) {
    return StringUtil.capitalizeWords(strategy.splitIntoWords(getXmlElementName()), true);
  }

  @Nullable
  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return getAnnotation(0, annotationClass);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final FixedChildDescriptionImpl that = (FixedChildDescriptionImpl)o;

    if (myCount != that.myCount) return false;
    if (!Arrays.equals(myGetterMethods, that.myGetterMethods)) return false;

    return true;
  }

  public String toString() {
    return getXmlElementName() + " " + getGetterMethod(0) + 
           " " + getType();
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + myCount;
    return result;
  }
}
