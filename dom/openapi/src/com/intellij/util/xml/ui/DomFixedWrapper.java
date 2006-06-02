/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public class DomFixedWrapper<T> extends DomWrapper<T>{
  private final GenericDomValue myDomElement;

  public DomFixedWrapper(final GenericDomValue<? extends T> domElement) {
    myDomElement = domElement;
  }

  public DomElement getWrappedElement() {
    return myDomElement;
  }

  public void setValue(final T value) throws IllegalAccessException, InvocationTargetException {
    DomUIFactory.SET_VALUE_METHOD.invoke(getWrappedElement(), value);
  }

  public T getValue() throws IllegalAccessException, InvocationTargetException {
    final DomElement element = getWrappedElement();
    return element.isValid() ? (T)DomUIFactory.GET_VALUE_METHOD.invoke(element) : null;
  }

  @NotNull
  public DomElement getExistingDomElement() {
    return myDomElement;
  }


}
