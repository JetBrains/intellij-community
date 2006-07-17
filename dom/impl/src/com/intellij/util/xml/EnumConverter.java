/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author peter
 */
public class EnumConverter<T extends Enum> extends ResolvingConverter<T>{
  private static final FactoryMap<Class,EnumConverter> ourCache = new FactoryMap<Class, EnumConverter>() {
    @NotNull
    protected EnumConverter create(final Class key) {
      return new EnumConverter(key);
    }
  };
  private final Class<T> myType;

  private EnumConverter(final Class<T> aClass) {
    myType = aClass;
  }

  public static <T extends Enum> EnumConverter<T>  createEnumConverter(Class<T> aClass) {
    return ourCache.get(aClass);
  }

  private String getStringValue(final T anEnum) {
    return NamedEnumUtil.getEnumValueByElement(anEnum);
  }

  public final T fromString(final String s, final ConvertContext context) {
    return s==null?null:(T)NamedEnumUtil.getEnumElementByValue((Class)myType, s);
  }

  public final String toString(final T t, final ConvertContext context) {
    return t == null? null:getStringValue(t);
  }

  @NotNull
  public Collection<? extends T> getVariants(final ConvertContext context) {
    return Arrays.asList(myType.getEnumConstants());
  }
}
