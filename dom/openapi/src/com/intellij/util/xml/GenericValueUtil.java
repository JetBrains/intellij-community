/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class GenericValueUtil {
  private GenericValueUtil() {
  }

  public static NullableFunction<GenericValue, String> STRING_VALUE = new NullableFunction<GenericValue, String>() {
    public String fun(final GenericValue genericValue) {
      return genericValue.getStringValue();
    }
  };
  public static NullableFunction<GenericValue, Object> OBJECT_VALUE = new NullableFunction<GenericValue, Object>() {
    public Object fun(final GenericValue genericValue) {
      return genericValue.getValue();
    }
  };


  public static boolean containsString(final List<? extends GenericValue<?>> list, String value) {
    for (GenericValue<?> o : list) {
      if (Comparing.equal(value, o.getStringValue())) return true;
    }
    return false;
  }

  public static <T> boolean containsValue(final List<? extends GenericValue<T>> list, T value) {
    for (GenericValue<T> o : list) {
      if (Comparing.equal(value, o.getValue())) return true;
    }
    return false;
  }

  @NotNull
  public static <T> Collection<T> getValueCollection(final Collection<? extends GenericValue<T>> collection, Collection<T> result) {
    for (GenericValue<T> o : collection) {
      ContainerUtil.addIfNotNull(o.getValue(), result);
    }
    return result;
  }

  @NotNull
  public static Collection<String> getStringCollection(final Collection<? extends GenericValue> collection, Collection<String> result) {
    for (GenericValue o : collection) {
      ContainerUtil.addIfNotNull(o.getStringValue(), result);
    }
    return result;
  }

}
