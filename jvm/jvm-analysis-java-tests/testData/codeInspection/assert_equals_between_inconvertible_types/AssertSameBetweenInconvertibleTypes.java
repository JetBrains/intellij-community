package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.Assert.assertSame;

class MyTest {
  @org.junit.jupiter.api.Test
  void myTest() {
    <warning descr="'assertSame()' between objects of inconvertible types 'String' and 'int'">assertSame</warning>("foo", 2);
    <warning descr="'assertSame()' between objects of inconvertible types 'int[]' and 'int'">assertSame</warning>(new int[2], 2);
    assertSame(1, 2); // ok
  }
}