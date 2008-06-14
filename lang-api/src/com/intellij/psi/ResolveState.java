/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import gnu.trove.THashMap;

import java.util.Map;

public class ResolveState {
  private final Map<Object, Object> myValues = new THashMap<Object, Object>(2);
  private static final ResolveState ourInitialState = new ResolveState();

  public static ResolveState initial() {
    return ourInitialState;
  }

  public static <T> void defaultsTo(Key<T> key, T value) {
    ourInitialState.myValues.put(key, value);
  }

  public <T> ResolveState put(Key<T> key, T value) {
    final ResolveState copy = new ResolveState();
    copy.myValues.putAll(myValues);
    copy.myValues.put(key, value);
    return copy;
  }

  @SuppressWarnings({"unchecked"})
  public <T> T get(Key<T> key) {
    return (T)myValues.get(key);
  }
}