// "Convert to record class" "true-preview"
import java.lang.annotation.*;
import java.util.*;

@Target(ElementType.TYPE_USE)
@interface TA {
  int value();
}

@interface FA {}

class A {
  class B{}
}

class <caret>X {
  private final @FA List<@TA(1) String> strings;
  private final @FA @TA(4) A.@TA(5) B b;
  private final @FA String @TA(2) [] arr;

  private X(@FA List<String> strings, @FA A.B b, @FA String... arr) {
    this.strings = strings;
    this.b = b;
    this.arr = arr;
  }
}
