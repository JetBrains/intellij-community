// "Convert to local variables" "true-preview"

import java.util.*;

class Foo {

    void test2() {
        List<String> x1 = new ArrayList<>();
    System.out.println(x1);
    Runnable r = () -> {
        List<String> x = new ArrayList<>(); // could be local
        System.out.println(x);
    };
  }
}