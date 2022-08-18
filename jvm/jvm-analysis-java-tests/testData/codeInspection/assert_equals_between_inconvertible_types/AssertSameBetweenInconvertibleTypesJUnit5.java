package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.jupiter.api.Assertions.assertSame;

class MyTest {
  @org.junit.jupiter.api.Test
  void myTest() {
    <warning descr="'assertSame()' between objects of inconvertible types 'int' and 'String'">assertSame</warning>(1, "", "Foo");
    <warning descr="'assertSame()' between objects of inconvertible types 'int' and 'int[]'">assertSame</warning>(1, new int[2], () -> "Foo in supplier");
    assertSame(1, 2, "message"); // ok
    assertSame(1, 2, () -> "message"); // ok
  }
}