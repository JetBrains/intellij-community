package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.assertj.core.api.Assertions.assertThat;

class MyTest {
  @org.junit.jupiter.api.Test
  void myTest() {
    assertThat(1).<warning descr="'isSameAs()' between objects of inconvertible types 'String' and 'int'">isSameAs</warning>("foo");
    assertThat("foo").describedAs("foo").<warning descr="'isSameAs()' between objects of inconvertible types 'int' and 'String'">isSameAs</warning>(2);
    assertThat(new int[2]).as("array").<warning descr="'isSameAs()' between objects of inconvertible types 'int' and 'int[]'">isSameAs</warning>(2);
    assertThat(1).isSameAs(2); // ok
  }
}
