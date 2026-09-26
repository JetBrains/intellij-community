// "Convert to local variables" "true-preview"

import java.util.*;

class Foo {
  private List<String> <caret>x;

  void test2() {
    x = new ArrayList<>();
    System.out.println(x);
    Runnable r = () -> {
      x = new ArrayList<>(); // could be local
      System.out.println(x);
    };
  }
}