/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.JavaMethod;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;

/**
 * @author peter
 */
public interface DomFixedChildDescription extends DomChildrenDescription {
  int getCount();
  JavaMethod getGetterMethod(int index);
  void initConcreteClass(final DomElement parent, final Class<? extends DomElement> aClass);

  @Nullable
  <T extends Annotation> T getAnnotation(int index, Class<? extends T> annotationClass);
}
