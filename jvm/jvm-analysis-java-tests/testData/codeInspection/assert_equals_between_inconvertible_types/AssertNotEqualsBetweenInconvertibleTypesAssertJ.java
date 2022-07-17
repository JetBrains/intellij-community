package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import org.assertj.core.api.Assertions;

class MyTest {
  @org.junit.jupiter.api.Test
  void myTest() {
    Assertions.assertThat("java").as("test").<weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int' and 'String'">isNotEqualTo</weak_warning>(1);
    Assertions.assertThat(new int[0]).describedAs("test").<weak_warning descr="Possibly redundant assertion: incompatible types are compared 'double' and 'int[]'">isNotEqualTo</weak_warning>(1.0);
    Assertions.assertThat(new int[0]).isNotEqualTo(new int[1]); //ok
  }
}
