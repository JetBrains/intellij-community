// "Replace 'switch' with 'if'" "true-preview"

import org.jetbrains.annotations.NotNull;

class Test {

  void foo(@NotNull Object o) {
    switch<caret> (o) {
      case String s -> System.out.println("one");
      case Integer i -> System.out.println("two");
      case default -> System.out.println("default");
    }
  }
}