// "Replace Implements with Static Import" "true"

import static I.FOO;

public class X {
  void foo() {
    System.out.println(FOO);
  }
}

interface I {
  String FOO = "foo";
}