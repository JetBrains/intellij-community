// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class ResolveState {
  private static final ResolveState ourInitialState = new ResolveState();

  public static @NotNull ResolveState initial() {
    return ourInitialState;
  }

  public @NotNull <T> ResolveState put(@NotNull Key<T> key, T value) {
    return new OneElementResolveState<>(key, value);
  }

  public @NotNull ResolveState putAll(@NotNull ResolveState state) {
    return state;
  }

  public <T> T get(@NotNull Key<T> key) {
    if (key instanceof KeyWithDefaultValue) {
      return ((KeyWithDefaultValue<T>)key).getDefaultValue();
    }
    return null;
  }

  private static final class OneElementResolveState<K> extends ResolveState {
    private final @NotNull Key<K> myKey;
    private final K myValue;

    private OneElementResolveState(@NotNull Key<K> key, K value) {
      myKey = key;
      myValue = value;
    }

    @Override
    public @NotNull <T> ResolveState put(@NotNull Key<T> key, T value) {
      if (myKey.equals(key)) {
        return new OneElementResolveState<>(key, value);
      }

      return new TwoElementResolveState<>(myKey, myValue, key, value);
    }

    @Override
    public @NotNull ResolveState putAll(@NotNull ResolveState state) {
      return state.get(myKey) == null ? state.put(myKey, myValue) : state;
    }

    @Override
    public <T> T get(@NotNull Key<T> key) {
      T value = myKey.equals(key) ? (T)myValue : null;
      if (value == null && key instanceof KeyWithDefaultValue) {
        return ((KeyWithDefaultValue<T>)key).getDefaultValue();
      }
      return value;
    }
  }

  private static class TwoElementResolveState<K1,K2> extends ResolveState {
    private final @NotNull Key<K1> myKey1;
    private final K1 myValue1;
    private final @NotNull Key<K2> myKey2;
    private final K2 myValue2;

    TwoElementResolveState(@NotNull Key<K1> key1, K1 value1, @NotNull Key<K2> key2, K2 value2) {
      myKey1 = key1;
      myValue1 = value1;
      myKey2 = key2;
      myValue2 = value2;
    }

    @Override
    public @NotNull <T> ResolveState put(@NotNull Key<T> key, T value) {
      if (myKey1.equals(key)) {
        return new TwoElementResolveState<>(key, value, myKey2, myValue2);
      }
      if (myKey2.equals(key)) {
        return new TwoElementResolveState<>(myKey1, myValue1, key, value);
      }

      return new ManyElementResolveState(this, key, value);
    }

    @Override
    public @NotNull ResolveState putAll(@NotNull ResolveState state) {
      if (state == ourInitialState) {
        return this;
      }
      else if (state instanceof OneElementResolveState) {
        OneElementResolveState<Object> oneState = (OneElementResolveState<Object>)state;
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
      T value;
      if (myKey1.equals(key)) {
        value = (T)myValue1;
      }
      else if (myKey2.equals(key)) {
        value = (T)myValue2;
      }
      else {
        value = null;
      }

      if (value == null && key instanceof KeyWithDefaultValue) {
        return ((KeyWithDefaultValue<T>)key).getDefaultValue();
      }
      return value;
    }
  }

  private static final class ManyElementResolveState extends ResolveState {
    private final Map<Key<?>, Object> myValues = new HashMap<>();

    ManyElementResolveState(@NotNull ResolveState state1, @NotNull ResolveState state2) {
      putAllNoCopy(state1);
      putAllNoCopy(state2);
    }

    ManyElementResolveState(@NotNull ResolveState state, @NotNull Key<?> key, Object value) {
      putAllNoCopy(state);
      myValues.put(key, value);
    }

    private void putAllNoCopy(@NotNull ResolveState other) {
      if (other instanceof OneElementResolveState) {
        OneElementResolveState<Object> oneState = (OneElementResolveState<Object>)other;
        myValues.put(oneState.myKey, oneState.myValue);
      }
      else if (other instanceof TwoElementResolveState) {
        TwoElementResolveState<Object,Object> twoState = (TwoElementResolveState<Object,Object>)other;
        myValues.put(twoState.myKey1, twoState.myValue1);
        myValues.put(twoState.myKey2, twoState.myValue2);
      }
      else if (other instanceof ManyElementResolveState) {
        ManyElementResolveState manyState = (ManyElementResolveState)other;
        myValues.putAll(manyState.myValues);
      }
    }

    @Override
    public @NotNull <T> ResolveState put(@NotNull Key<T> key, T value) {
      return new ManyElementResolveState(this, key, value);
    }

    @Override
    public @NotNull ResolveState putAll(@NotNull ResolveState state) {
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
