/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface GenericDomValue<T> extends DomElement, GenericValue<T>{

  @NotNull
  Converter<T> getConverter();

  @TagValue
  void setStringValue(String value);

  void setValue(T value);

}
