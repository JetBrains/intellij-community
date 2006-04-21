/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public interface DomCollectionChildDescription extends DomChildrenDescription {
  Method getGetterMethod();
  Method getIndexedAdderMethod();
  Method getAdderMethod();

  DomElement addValue(DomElement parent);
  DomElement addValue(DomElement parent, int index);
  DomElement addValue(DomElement parent, Class aClass);
  DomElement addValue(DomElement parent, Class aClass, int index);

  Method getClassAdderMethod();

  Method getIndexedClassAdderMethod();

  Method getInvertedIndexedClassAdderMethod();

  boolean isRequiredNotEmpty();
}
