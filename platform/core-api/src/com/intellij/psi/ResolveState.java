// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@SuppressWarnings({"unchecked"})
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

  @NotNull
  public ResolveState putAll(@NotNull ResolveState state) {
    return state;
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

    @NotNull
    @Override
    public ResolveState putAll(@NotNull ResolveState state) {
      return state.get(myKey) == null ? state.put(myKey, myValue) : state;
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

    @NotNull
    @Override
    public ResolveState putAll(@NotNull ResolveState state) {
      if (state == ourInitialState) {
        return this;
      }
      else if (state instanceof OneElementResolveState) {
        OneElementResolveState oneState = (OneElementResolveState)state;
        return put(oneState.myKey, oneState.myValue);
      }
      boolean has1 = state.get(myKey1) != null;
      boolean has2 = state.get(myKey2) != null;
      if (has1 && has2) {
        return state;
      }
      else if (has1) {
        return state.put(myKey2, myValue2);
      }
      else if (has2) {
        return state.put(myKey1, myValue1);
      }
      // at this point our keys are not in other and other has at least 2 keys
      return new ManyElementResolveState(state, this);
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
    private final Map<Object, Object> myValues = new THashMap<>();

    ManyElementResolveState(@NotNull ResolveState state1, @NotNull ResolveState state2) {
      putAllNoCopy(state1);
      putAllNoCopy(state2);
    }

    ManyElementResolveState(@NotNull ResolveState state, @NotNull Key key, Object value) {
      putAllNoCopy(state);
      myValues.put(key, value);
    }

    private void putAllNoCopy(@NotNull ResolveState other) {
      if (other instanceof OneElementResolveState) {
        OneElementResolveState oneState = (OneElementResolveState)other;
        myValues.put(oneState.myKey, oneState.myValue);
      }
      else if (other instanceof TwoElementResolveState) {
        TwoElementResolveState twoState = (TwoElementResolveState)other;
        myValues.put(twoState.myKey1, twoState.myValue1);
        myValues.put(twoState.myKey2, twoState.myValue2);
      }
      else if (other instanceof ManyElementResolveState) {
        ManyElementResolveState manyState = (ManyElementResolveState)other;
        myValues.putAll(manyState.myValues);
      }
    }

    @NotNull
    @Override
    public <T> ResolveState put(@NotNull Key<T> key, T value) {
      return new ManyElementResolveState(this, key, value);
    }

    @NotNull
    @Override
    public ResolveState putAll(@NotNull ResolveState state) {
      if (state == ourInitialState) return this;
      return new ManyElementResolveState(this, state);
    }

    @Override
    public <T> T get(@NotNull Key<T> key) {
      final T value = (T)myValues.get(key);
      if (value == null && key instanceof KeyWithDefaultValue) {
        return ((KeyWithDefaultValue<T>)key).getDefaultValue();
      }
      return value;
    }
  }
}
