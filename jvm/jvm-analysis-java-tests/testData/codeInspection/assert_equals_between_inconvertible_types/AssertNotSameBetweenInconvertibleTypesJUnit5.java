package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.jupiter.api.Assertions.assertNotSame;

class MyTest {
  @org.junit.jupiter.api.Test
  void myTest() {
    <warning descr="Redundant assertion: incompatible types are compared 'String' and 'int'">assertNotSame</warning>("java", 1, "message");
    <warning descr="Redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotSame</warning>(new int[0], 1.0, "message");
    assertNotSame(new int[0], new int[1]); //ok
  }
}