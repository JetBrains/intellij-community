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

  public int compareTo(SortedIntSet t) {
    if (t == this) return 0;
    if (t.size() != size()) return size() - t.size();
    for (int i = 0; i < size(); i++) {
      if (_data[i] != t._data[i]) return _data[i] - t._data[i];
    }
    return 0;
  }
}
