/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class FixedChildDescriptionImpl extends DomChildDescriptionImpl implements DomFixedChildDescription {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.FixedChildDescriptionImpl");
  private final Method[] myGetterMethods;
  private final int myCount;

  public FixedChildDescriptionImpl(final String tagName, final Type type, final int count, final Method[] getterMethods) {
    super(tagName, type);
    assert getterMethods.length == count;
    myCount = count;
    myGetterMethods = getterMethods;
  }

  public Method getGetterMethod(int index) {
    return myGetterMethods[index];
  }

  public void initConcreteClass(final DomElement parent, final Class<? extends DomElement> aClass) {
    DomManagerImpl.getDomInvocationHandler(parent).setFixedChildClass(getXmlElementName(), aClass);
  }

  @Nullable
  public <T extends Annotation> T getAnnotation(int index, Class<? extends T> annotationClass) {
    return DomReflectionUtil.findAnnotationDFS(getGetterMethod(index), annotationClass);
  }

  public int getCount() {
    return myCount;
  }

  public List<? extends DomElement> getValues(final DomElement element) {
    final ArrayList<DomElement> result = new ArrayList<DomElement>();
    for (Method method : myGetterMethods) {
      if (method != null) {
        try {
          //assert method.getDeclaringClass().isInstance(element) : method.getDeclaringClass() + " " + element.getClass();
          result.add((DomElement) method.invoke(element));
        }
        catch (IllegalArgumentException e) {
          LOG.error(e);
        }
        catch (IllegalAccessException e) {
          LOG.error(e);
        }
        catch (InvocationTargetException e) {
          final Throwable throwable = e.getCause();
          if (throwable instanceof ProcessCanceledException) {
            throw (ProcessCanceledException)throwable;
          }
          LOG.error(e);
        }
      }
    }
    return result;
  }

  public String getCommonPresentableName(DomNameStrategy strategy) {
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
