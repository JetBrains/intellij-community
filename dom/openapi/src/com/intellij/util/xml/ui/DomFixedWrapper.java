/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;

import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public class DomFixedWrapper<T> implements DomWrapper<T>{
  private final GenericDomValue myDomElement;

  public DomFixedWrapper(final GenericDomValue<? extends T> domElement) {
    myDomElement = domElement;
  }

  public DomElement getDomElement() {
    return myDomElement;
  }

  public void setValue(final T value) throws IllegalAccessException, InvocationTargetException {
    DomUIFactory.SET_VALUE_METHOD.invoke(getDomElement(), value);
  }

  public T getValue() throws IllegalAccessException, InvocationTargetException {
    final DomElement element = getDomElement();
    return element.isValid() ? (T)DomUIFactory.GET_VALUE_METHOD.invoke(element) : null;
  }

  public boolean isValid() {
    return myDomElement.isValid();
  }

  public Project getProject() {
    return myDomElement.getManager().getProject();
  }

  public GlobalSearchScope getResolveScope() {
    return myDomElement.getResolveScope();
  }

}
