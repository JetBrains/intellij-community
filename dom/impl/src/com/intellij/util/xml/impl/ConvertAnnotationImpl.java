/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.Converter;

import java.lang.annotation.Annotation;

/**
 * @author peter
*/
public class ConvertAnnotationImpl implements Convert {
  private final Converter myConverter;
  private final boolean mySoft;

  public ConvertAnnotationImpl(final Converter converter, final boolean soft) {
    myConverter = converter;
    mySoft = soft;
  }

  public Class<? extends Annotation> annotationType() {
    return Convert.class;
  }

  public Converter getConverter() {
    return myConverter;
  }

  public Class<? extends Converter> value() {
    return myConverter.getClass();
  }

  public boolean soft() {
    return mySoft;
  }

}
