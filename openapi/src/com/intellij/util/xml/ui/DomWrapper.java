/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;

import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public interface DomWrapper<T> {
  DomElement getDomElement();
  void setValue(T value) throws IllegalAccessException, InvocationTargetException;
  T getValue() throws IllegalAccessException, InvocationTargetException;
}
