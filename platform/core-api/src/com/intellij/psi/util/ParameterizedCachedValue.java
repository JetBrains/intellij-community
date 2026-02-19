// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

/**
 * A variation on {@link CachedValue} which allows reusing the same value provider in all cached values, make it static
 * and thus save some memory.
 * In most cases {@link CachedValue} should be preferred:
 * the memory benefits of parameterization are negligible, while the client code becomes longer, more complicated and error-prone.<p></p>
 *
 * Note that this object holds just one cached value, not a map from parameters to their respective results.
 * So the {@code value} argument of {@link #getValue(Object)} method must always be the same
 * for the same {@code ParameterizedCachedValue} (most likely it'll be the object where this {@code ParameterizedCachedValue}
 * is stored as a field or user data). Otherwise, the cache would contain some accidental data
 * depending on which thread called it first with which argument.<p></p>
 */
public interface ParameterizedCachedValue<T, P> {

  T getValue(P param);

  /**
   * @return the object calculating the value to cache
   */
  ParameterizedCachedValueProvider<T,P> getValueProvider();

  /**
   * @return whether there is a cached result inside this object, and it's not outdated
   */
  boolean hasUpToDateValue();
}
