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

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import gnu.trove.THashMap;

import java.util.Map;

public class ResolveState {
  private final Map<Object, Object> myValues = new THashMap<Object, Object>(2);
  private static final ResolveState ourInitialState = new ResolveState();

  public static ResolveState initial() {
    return ourInitialState;
  }

  public <T> ResolveState put(Key<T> key, T value) {
    final ResolveState copy = new ResolveState();
    copy.myValues.putAll(myValues);
    copy.myValues.put(key, value);
    return copy;
  }

  @SuppressWarnings({"unchecked"})
  public <T> T get(Key<T> key) {
    final T value = (T)myValues.get(key);
    if (value == null && key instanceof KeyWithDefaultValue) {
      return ((KeyWithDefaultValue<T>) key).getDefaultValue();
    }
    return value;
  }
}