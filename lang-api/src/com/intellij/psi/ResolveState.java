/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import gnu.trove.THashMap;

import java.util.Map;

public class ResolveState {
  private Map<Object, Object> myValues = null;
  private static final ResolveState ourInitialState = new ResolveState();

  public static ResolveState initial() {
    return ourInitialState;
  }

  public static <T> void defaultsTo(Key<T> key, T value) {
    initial().put(key, value);
  }

  public <T> ResolveState put(Key<T> key, T value) {
    final ResolveState copy = new ResolveState();
    copy.myValues = new THashMap<Object, Object>();
    if (myValues != null) {
      copy.myValues.putAll(myValues);
    }
    copy.myValues.put(key, value);
    return copy;
  }

  @SuppressWarnings({"unchecked"})
  public <T> T get(Key<T> key) {
    return myValues != null ? (T)myValues.get(key) : null;
  }
}