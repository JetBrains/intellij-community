/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.JavaMethod;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface DomCollectionChildDescription extends DomChildrenDescription {

  DomCollectionChildDescription[] EMPTY_ARRAY = new DomCollectionChildDescription[0];
  
  @Nullable
  JavaMethod getGetterMethod();
  @Nullable
  JavaMethod getIndexedAdderMethod();
  @Nullable
  JavaMethod getAdderMethod();

  DomElement addValue(DomElement parent);
  DomElement addValue(DomElement parent, int index);
  DomElement addValue(DomElement parent, Type type);
  DomElement addValue(DomElement parent, Type type, int index);

  @Nullable
  JavaMethod getClassAdderMethod();

  @Nullable
  JavaMethod getIndexedClassAdderMethod();

  @Nullable
  JavaMethod getInvertedIndexedClassAdderMethod();

}
