/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.InstanceMap;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
class ConverterManagerImpl implements ConverterManager {
  private final InstanceMap<Converter> myConverterInstances = new InstanceMap<Converter>();
  private final Map<Class,Converter> mySimpleConverters = new HashMap<Class, Converter>();

  ConverterManagerImpl() {
    mySimpleConverters.put(int.class, Converter.INTEGER_CONVERTER);
    mySimpleConverters.put(Integer.class, Converter.INTEGER_CONVERTER);
    mySimpleConverters.put(boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    mySimpleConverters.put(Boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    mySimpleConverters.put(String.class, Converter.EMPTY_CONVERTER);
    mySimpleConverters.put(PsiClass.class, Converter.PSI_CLASS_CONVERTER);
    mySimpleConverters.put(PsiType.class, Converter.PSI_TYPE_CONVERTER);
  }

  public void addConverter(Class clazz, Converter converter) {
    mySimpleConverters.put(clazz, converter);
  }

  @NotNull
  public final Converter getConverterInstance(final Class<? extends Converter> converterClass) {
    return myConverterInstances.get(converterClass);
  }

  @Nullable
  public final Converter getConverterByClass(final Class<?> convertingClass) {
    final Converter converter = mySimpleConverters.get(convertingClass);
    if (converter != null) {
      return converter;
    }

    if (Enum.class.isAssignableFrom(convertingClass)) {
      return EnumConverter.createEnumConverter((Class<? extends Enum>)convertingClass);
    }
    if (DomElement.class.isAssignableFrom(convertingClass)) {
      return DomResolveConverter.createConverter((Class<? extends DomElement>)convertingClass);
    }
    return null;
  }

  public void registerConverterImplementation(Class<? extends Converter> converterInterface, Converter converterImpl) {
    myConverterInstances.put(converterInterface, converterImpl);
  }
}
