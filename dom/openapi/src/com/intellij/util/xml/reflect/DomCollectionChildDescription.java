/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.JavaMethod;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface DomCollectionChildDescription extends DomChildrenDescription {

  DomCollectionChildDescription[] EMPTY_ARRAY = new DomCollectionChildDescription[0];
  
  JavaMethod getGetterMethod();
  JavaMethod getIndexedAdderMethod();
  JavaMethod getAdderMethod();

  DomElement addValue(DomElement parent);
  DomElement addValue(DomElement parent, int index);
  DomElement addValue(DomElement parent, Type type);
  DomElement addValue(DomElement parent, Type type, int index);

  JavaMethod getClassAdderMethod();

  JavaMethod getIndexedClassAdderMethod();

  JavaMethod getInvertedIndexedClassAdderMethod();

}
