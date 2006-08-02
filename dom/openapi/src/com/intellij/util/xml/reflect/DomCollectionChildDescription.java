/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.JavaMethod;

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
  DomElement addValue(DomElement parent, Class aClass);
  DomElement addValue(DomElement parent, Class aClass, int index);

  JavaMethod getClassAdderMethod();

  JavaMethod getIndexedClassAdderMethod();

  JavaMethod getInvertedIndexedClassAdderMethod();

}
