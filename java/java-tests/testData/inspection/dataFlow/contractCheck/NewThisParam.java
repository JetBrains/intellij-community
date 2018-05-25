package org.jetbrains.annotations;

import java.util.*;

class Test {
  @Contract("_ -> new")
  public List<String> list(int size) {
    if(size < 0) {
      // throwing is not a contract violation
      throw new IllegalArgumentException();
    }
    ArrayList<String> list = new ArrayList<>();
    for(int i=0; i<size; i++) list.add("");
    return list;
  }

  @Contract("_ -> param1")
  public static int test(int x) {
    <warning descr="Return value of clause '_ -> param1' could be replaced with 'fail' as method always fails">throw new IllegalArgumentException();</warning>
  }

  // Do not report: could be intended to override in subclasses
  @Contract("_ -> param1")
  public int testNonStatic(int x) {
    throw new UnsupportedOperationException();
  }

  @Contract("-> this")
  public Test returnThis() {
    return <warning descr="Contract clause ' -> this' is violated">null</warning>;
  }

  class Sub extends Test {
    // Do not report: contract is inherited
    public int testNonStatic(int x) {
      throw new UnsupportedOperationException();
    }

  }
}