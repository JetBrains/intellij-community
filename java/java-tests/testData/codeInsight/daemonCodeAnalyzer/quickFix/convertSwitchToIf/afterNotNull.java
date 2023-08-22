// "Replace 'switch' with 'if'" "true-preview"

import org.jetbrains.annotations.NotNull;

class Test {

  void foo(@NotNull Object o) {
      if (o instanceof String) {
          System.out.println("one");
      } else if (o instanceof Integer) {
          System.out.println("two");
      } else {
          System.out.println("default");
      }
  }
}