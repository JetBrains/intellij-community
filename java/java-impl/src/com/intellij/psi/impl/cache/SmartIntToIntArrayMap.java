/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache;

import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class SmartIntToIntArrayMap {
  @Nullable TIntObjectHashMap<TIntArrayList> myMultipleValuesMap;
  TIntIntHashMap mySingleValueMap = new TIntIntHashMap(10, 0.9f);

  public int[] keys() {
    int[] multiKeys = myMultipleValuesMap != null ? myMultipleValuesMap.keys() : ArrayUtil.EMPTY_INT_ARRAY;
    int[] singleKeys = mySingleValueMap.keys();

    if (singleKeys.length == 0) return multiKeys;

    int[] combinedKeys = new int[multiKeys.length + singleKeys.length];
    System.arraycopy(multiKeys, 0, combinedKeys, 0, multiKeys.length);
    System.arraycopy(singleKeys, 0, combinedKeys, multiKeys.length, singleKeys.length);
    return combinedKeys;
  }

  public void addOccurence(int key, int value) {
    if (myMultipleValuesMap != null && myMultipleValuesMap.containsKey(key)) {
      addToMultimap(key, value);
    }
    else if (mySingleValueMap.containsKey(key)) {
      int storedId = mySingleValueMap.get(key);
      if (storedId == value) return;
      mySingleValueMap.remove(key);
      addToMultimap(key, storedId);
      addToMultimap(key, value);
    }
    else {
      mySingleValueMap.put(key, value);
    }
  }

  private void addToMultimap(int key, int value) {
    if (myMultipleValuesMap == null) {
      myMultipleValuesMap = new TIntObjectHashMap<>(10, 0.9f);
    }

    final TIntObjectHashMap<TIntArrayList> map = myMultipleValuesMap;
    TIntArrayList list = map.get(key);
    if (list == null) {
      list = new TIntArrayList(3);
      map.put(key, list);
    }

    if (!list.contains(value)) list.add(value);
  }

  public void removeOccurence(int key, int value) {
    if (mySingleValueMap.containsKey(key)) {
      mySingleValueMap.remove(key);
    }
    else {
      removeFromMultiMap(key, value);
    }
  }

  private void removeFromMultiMap(int key, int value) {
    final TIntObjectHashMap<TIntArrayList> map = myMultipleValuesMap;
    if (map == null) return;
    TIntArrayList list = map.get(key);
    if (list != null) {
      int offset = list.indexOf(value);
      if (offset != -1) {
        list.remove(offset);
        if (list.isEmpty()) {
          map.remove(key);
        }
      }
    }
  }

  public int[] get(int key) {
    if (mySingleValueMap.containsKey(key)) {
      int id = mySingleValueMap.get(key);
      return new int[]{id};
    }

    return getFromMultimap(key);
  }

  private int[] getFromMultimap(int key) {
    TIntArrayList res = myMultipleValuesMap != null ? myMultipleValuesMap.get(key) : null;
    if (res == null) return ArrayUtil.EMPTY_INT_ARRAY;
    return res.toNativeArray();
  }
}
