/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface ConverterManager {
  @NotNull
  Converter getConverter(Class converterClass) throws InstantiationException, IllegalAccessException;

  void registerConverter(Class converterClass, Converter converter);
}
