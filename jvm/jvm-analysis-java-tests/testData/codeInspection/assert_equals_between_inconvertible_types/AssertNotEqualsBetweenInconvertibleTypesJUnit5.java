package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MyTest {
  @org.junit.jupiter.api.Test
  void myTest() {
    <weak_warning descr="Possible redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</weak_warning>("java", 1, "message");
    <weak_warning descr="Possible redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</weak_warning>(new int[0], 1.0, "message");
    assertNotEquals(new int[0], new int[1]); //ok
  }
}