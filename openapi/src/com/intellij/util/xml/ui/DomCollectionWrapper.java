/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * @author peter
 */
public class DomCollectionWrapper<T> implements DomWrapper<T>{
  private final DomElement myDomElement;
  private final DomCollectionChildDescription myChildDescription;
  private final Method mySetter;
  private final Method myGetter;

  public DomCollectionWrapper(final DomElement domElement,
                              final DomCollectionChildDescription childDescription) {
    this(domElement, childDescription, 
         DomUIFactory.findMethod(DomUtil.getRawType(childDescription.getType()), "setValue"),
         DomUIFactory.findMethod(DomUtil.getRawType(childDescription.getType()), "getValue"));
  }

  public DomCollectionWrapper(final DomElement domElement,
                              final DomCollectionChildDescription childDescription,
                              final Method setter,
                              final Method getter) {
    myDomElement = domElement;
    myChildDescription = childDescription;
    mySetter = setter;
    myGetter = getter;
  }

  public DomElement getDomElement() {
    final List<? extends DomElement> list = myChildDescription.getValues(myDomElement);
    return list.isEmpty() ? null : list.get(0);
  }

  public void setValue(final T value) throws IllegalAccessException, InvocationTargetException {
    final List<? extends DomElement> list = myChildDescription.getValues(myDomElement);
    final DomElement domElement;
    if (list.isEmpty()) {
      domElement = myChildDescription.addValue(myDomElement);
    } else {
      domElement = list.get(0);
    }
    mySetter.invoke(domElement, value);
  }

  public T getValue() throws IllegalAccessException, InvocationTargetException {
    if (!myDomElement.isValid()) return null;
    final List<? extends DomElement> list = myChildDescription.getValues(myDomElement);
    return list.isEmpty() ? null : (T)myGetter.invoke(list.get(0));
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
