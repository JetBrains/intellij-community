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
package com.intellij.codeInspection.dataFlow;

import gnu.trove.TIntArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 3, 2003
 * Time: 6:16:20 PM
 * To change this template use Options | File Templates.
 */
public class SortedIntSet extends TIntArrayList implements Comparable<SortedIntSet> {
  public SortedIntSet() {
  }

  public SortedIntSet(int[] values) {
    super(values);
  }

  @Override
  public void add(int val) {
    for(int idx = 0; idx < size(); idx++) {
      int data = get(idx);
      if (data == val) return;
      if (data > val) {
        insert(idx, val);
        return;
      }
    }
    super.add(val);
  }

  @Override
  public void add(int[] vals) {
    for (int val : vals) {
      add(val);
    }
  }

  public void removeValue(int val) {
    int offset = indexOf(val);
    if (offset != -1) {
      remove(offset);
    }
  }

  @Override
  public int compareTo(SortedIntSet t) {
    if (t == this) return 0;
    if (t.size() != size()) return size() - t.size();
    for (int i = 0; i < size(); i++) {
      if (_data[i] != t._data[i]) return _data[i] - t._data[i];
    }
    return 0;
  }
}
