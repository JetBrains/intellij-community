/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@SuppressWarnings({"unchecked", "ConstantConditions"})
public class ResolveState {
  private static final ResolveState ourInitialState = new ResolveState();

  @NotNull
  public static ResolveState initial() {
    return ourInitialState;
  }

  @NotNull
  public <T> ResolveState put(@NotNull Key<T> key, T value) {
    return new OneElementResolveState(key, value);
  }

  public <T> T get(@NotNull Key<T> key) {
    if (key instanceof KeyWithDefaultValue) {
      return ((KeyWithDefaultValue<T>)key).getDefaultValue();
    }
    return null;
  }

  private static class OneElementResolveState extends ResolveState {
    @NotNull
    private final Key myKey;
    private final Object myValue;

    private OneElementResolveState(@NotNull Key key, Object value) {
      myKey = key;
      myValue = value;
    }

    @NotNull
    @Override
    public <T> ResolveState put(@NotNull Key<T> key, T value) {
      if (myKey.equals(key)) {
        return new OneElementResolveState(key, value);
      }

      return new TwoElementResolveState(myKey, myValue, key, value);
    }

    @Override
    public <T> T get(@NotNull Key<T> key) {
      Object value = myKey.equals(key) ? myValue : null;
      if (value == null && key instanceof KeyWithDefaultValue) {
        return ((KeyWithDefaultValue<T>)key).getDefaultValue();
      }
      return (T)value;
    }
  }

  private static class TwoElementResolveState extends ResolveState {
    @NotNull private final Key myKey1;
    private final Object myValue1;
    @NotNull private final Key myKey2;
    private final Object myValue2;

    TwoElementResolveState(@NotNull Key key1, Object value1, @NotNull Key key2, Object value2) {
      myKey1 = key1;
      myValue1 = value1;
      myKey2 = key2;
      myValue2 = value2;
    }

    @NotNull
    @Override
    public <T> ResolveState put(@NotNull Key<T> key, T value) {
      if (myKey1.equals(key)) {
        return new TwoElementResolveState(key, value, myKey2, myValue2);
      }
      if (myKey2.equals(key)) {
        return new TwoElementResolveState(myKey1, myValue1, key, value);
      }

      return new ManyElementResolveState(this, key, value);
    }

    @Override
    public <T> T get(@NotNull Key<T> key) {
      Object value;
      if (myKey1.equals(key)) {
        value = myValue1;
      }
      else if (myKey2.equals(key)) {
        value = myValue2;
      }
      else {
        value = null;
      }

      if (value == null && key instanceof KeyWithDefaultValue) {
        return ((KeyWithDefaultValue<T>)key).getDefaultValue();
      }
      return (T)value;
    }
  }

  private static class ManyElementResolveState extends ResolveState {
    private final Map<Object, Object> myValues = new THashMap<Object, Object>();

    ManyElementResolveState(@NotNull ManyElementResolveState parent, @NotNull Key key, Object value) {
      myValues.putAll(parent.myValues);
      myValues.put(key, value);
    }

    ManyElementResolveState(@NotNull TwoElementResolveState twoState, @NotNull Key key, Object value) {
      myValues.put(twoState.myKey1, twoState.myValue1);
      myValues.put(twoState.myKey2, twoState.myValue2);
      myValues.put(key, value);
    }

    @NotNull
    @Override
    public <T> ResolveState put(@NotNull Key<T> key, T value) {
      return new ManyElementResolveState(this, key, value);
    }

    @Override
    public <T> T get(@NotNull Key<T> key) {
      final T value = (T)myValues.get(key);
      if (value == null && key instanceof KeyWithDefaultValue) {
        return ((KeyWithDefaultValue<T>) key).getDefaultValue();
      }
      return value;
    }
  }
}
