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
    int idx = indexOf(val);
    if (idx != -1) return;
    for(idx = 0; idx < size(); idx++) {
      if (get(idx) > val) {
        insert(idx, val);
        return;
      }
    }
    super.add(val);
  }

  public void add(int[] vals) {
    for (int i = 0; i < vals.length; i++) {
      add(vals[i]);
    }
  }

  public void removeValue(int val) {
    remove(indexOf(val));
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
