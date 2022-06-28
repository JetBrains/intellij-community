package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.assertj.core.api.Assertions.assertThat;

class MyTest {
  @org.junit.jupiter.api.Test
  void myTest() {
    assertThat("java").as("test").<warning descr="Redundant assertion: incompatible types are compared 'int' and 'String'">isNotSameAs</warning>(1);
    assertThat(new int[0]).describedAs("test").<warning descr="Redundant assertion: incompatible types are compared 'double' and 'int[]'">isNotSameAs</warning>(1.0);
    assertThat(new int[0]).isNotSameAs(new int[1]); //ok
  }
}