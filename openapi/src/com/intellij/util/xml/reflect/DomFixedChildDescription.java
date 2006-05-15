/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.Required;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public interface DomFixedChildDescription extends DomChildrenDescription {
  int getCount();
  Method getGetterMethod(int index);
  void initConcreteClass(final DomElement parent, final Class<? extends DomElement> aClass);
  Required getRequiredAnnotation(int index);
}
