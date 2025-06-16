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

record X(@FA List<@TA(1) String> strings, @FA @TA(4) A.@TA(5) B b, @FA String @TA(2) ... arr) {
    X(@FA List<String> strings, @FA A.B b, @FA String... arr) {
        this.strings = strings;
        this.b = b;
        this.arr = arr;
    }
}
