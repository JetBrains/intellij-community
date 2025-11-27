package org.example;

import org.jspecify.annotations.*;

import java.util.*;

@NullMarked
class CustomOptionalTest {
  @Nullable
  public String a;

  Optional<String> returnNotNullableDelegate() {
    return Delegate.ofNullable(a);
  }

  Optional<String> returnNotNullable() {
    return Optional.ofNullable(a);
  }

  Comparator<String> getComparator() {
    return Comparator.<@Nullable String>naturalOrder();
  }
}

class TestList{
  static void test(String a) {
    if (List.<error descr="Cannot resolve method 'of' in 'List'">of</error>("1", a).getFirst() == null) {
      System.out.println("null");
    }
  }
}

class Delegate {
  static <L extends String> Optional<L> ofNullable(@Nullable L t) {
    return Optional.ofNullable(t);
  }
}