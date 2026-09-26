package com.siyeh.igtest.style.size_replaceable_by_is_empty;

import java.util.Collection;
import java.util.ArrayList;

public class SizeReplaceableByIsEmpty {

  boolean equalsToZero(String s) {
    return <warning descr="'s.length() == 0' can be replaced with 's.isEmpty()'">s.length() == 0</warning>;
  }

  boolean minusThanOne(String s) {
    return <warning descr="'s.length() < 1' can be replaced with 's.isEmpty()'">s.length() < 1</warning>;
  }

  boolean bas(StringBuilder b) {
    return b.length() == 0;
  }

  boolean collectionEqualsToZero(Collection c) {
    return <warning descr="'c.size() == 0' can be replaced with 'c.isEmpty()'">c.size() == 0</warning>;
  }

  boolean collectionMinusThanOne(Collection c) {
    return <warning descr="'c.size() < 1' can be replaced with 'c.isEmpty()'">c.size() < 1</warning>;
  }

  boolean parens(Collection c) {
    return <warning descr="'(c.size()) == (0)' can be replaced with 'c.isEmpty()'">(c.size()) == (0)</warning>;
  }

  class String {
    public int length() {
      return 1;
    }

    public boolean isEmpty() {
      return false;
    }
  }

  abstract class MyList<T> implements java.util.List<T>
  {
    public boolean isEmpty()
    {
      return this.size() == 0;
    }
  }

  boolean x(ArrayList<String> list) {
    return list.size() == 0;
  }
}
