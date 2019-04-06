/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.util;

/**
 * A variation on {@link CachedValue} which allows to reuse the same value provider in all cached values, make it static
 * and thus save some memory.
 * In most cases {@link CachedValue} should be preferred:
 * the memory benefits of parameterization are negligible, while the client code becomes longer, more complicated and error-prone.<p></p>
 *
 * Note that this object holds just one cached value, not a map from parameters to their respective results.
 * So the {@code value} argument of {@link #getValue(Object)} method must always be the same
 * for the same {@code ParameterizedCachedValue} (most likely it'll be the object where this {@code ParameterizedCachedValue}
 * is stored as a field or user data). Otherwise the cache would contain some accidental data
 * depending on which thread called it first with which argument.<p></p>
 */
public interface ParameterizedCachedValue<T, P> {

  T getValue(P param);

  ParameterizedCachedValueProvider<T,P> getValueProvider();

  boolean hasUpToDateValue();
}
