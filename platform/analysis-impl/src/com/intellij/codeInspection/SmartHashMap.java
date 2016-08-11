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
package com.intellij.codeInspection;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SingletonSet;
import gnu.trove.THashMap;
import gnu.trove.TObjectFunction;
import gnu.trove.TObjectObjectProcedure;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Hash set (based on THashSet) which is fast when contains one or zero elements (avoids to calculate hash codes and call equals whenever possible).
 * For other sizes it delegates to THashSet.
 * Null keys are NOT PERMITTED.
 */
public class SmartHashMap<K,V> extends THashMap<K,V> {
  private K theKey;   // contains the only element if size() == 1
  private V theValue;

  @Override
  public boolean containsKey(@NotNull Object key) {
    K theKey = this.theKey;
    if (theKey != null) {
      return eq(theKey, (K)key);
    }
    return !super.isEmpty() && super.containsKey(key);
  }

  @Override
  public V put(@NotNull K key, V value) {
    K theKey = this.theKey;
    if (theKey != null) {
      if (eq(theKey, key)) return theValue;
      super.put(theKey, theValue);
      this.theKey = null;
      // fallthrough
    }
    else if (super.isEmpty()) {
      this.theKey = key;
      theValue = value;
      return null;
    }
    return super.put(key, value);
  }

  private boolean eq(K obj, K theKey) {
    return theKey == obj || _hashingStrategy.equals(theKey, obj);
  }

  @Override
  public boolean equals(@NotNull Object other) {
    K theKey = this.theKey;
    if (theKey != null) {
      if (!(other instanceof Map) || ((Map)other).size() != 1 ) return false;
      Map.Entry<K, V> entry = ((Map<K, V>)other).entrySet().iterator().next();
      return eq(theKey, entry.getKey()) && Comparing.equal(theValue, entry.getValue());
    }

    return super.equals(other);
  }

  @Override
  public int hashCode() {
    K theKey = this.theKey;
    if (theKey != null) {
      return _hashingStrategy.computeHashCode(theKey);
    }
    return super.hashCode();
  }

  @Override
  public void clear() {
    theKey = null;
    theValue = null;
    super.clear();
  }

  @Override
  public int size() {
    K theKey = this.theKey;
    if (theKey != null) {
      return 1;
    }
    return super.size();
  }

  @Override
  public boolean isEmpty() {
    K theKey = this.theKey;
    return theKey == null && super.isEmpty();
  }

  @Override
  public V remove(@NotNull Object obj) {
    K theKey = this.theKey;
    if (theKey != null) {
      if (eq(theKey, (K)obj)) {
        this.theKey = null;
        V value = theValue;
        theValue = null;
        return value;
      }
      return null;
    }
    return super.remove(obj);
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    K theKey = this.theKey;
    if (theKey != null) {
      return new SingletonSet<>(theKey);
    }
    return super.keySet();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    K theKey = this.theKey;
    if (theKey != null) {
      return new SingletonSet<>(theValue);
    }
    return super.values();
  }

  @NotNull
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    K theKey = this.theKey;
    if (theKey != null) {
      return new SingletonSet<>(new AbstractMap.SimpleEntry<>(theKey, theValue));
    }
    return super.entrySet();
  }

  @Override
  public V get(Object key) {
    K theKey = this.theKey;
    if (theKey != null) {
      return eq(theKey, (K)key) ? theValue : null;
    }
    return super.get(key);
  }

  @Override
  public boolean containsValue(Object val) {
    K theKey = this.theKey;
    if (theKey != null) {
      return Comparing.equal(theValue, val);
    }
    return super.containsValue(val);
  }

  @Override
  public THashMap<K, V> clone() {
    throw new IncorrectOperationException();
  }

  @Override
  public void transformValues(TObjectFunction<V, V> function) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean retainEntries(TObjectObjectProcedure<K, V> procedure) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean forEachEntry(TObjectObjectProcedure<K, V> procedure) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean forEachValue(TObjectProcedure<V> procedure) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean forEachKey(TObjectProcedure<K> procedure) {
    throw new IncorrectOperationException();
  }
}
