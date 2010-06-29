// "Replace Implements with Static Import" "true"

import static I1.BAZZ;

public class X implements I {
  void bar() {
    System.out.println(FOO);
    System.out.println(BAZZ);
  }
}

interface I {
  String FOO = "foo";
}

interface I1 {
  String BAZZ = "bazz";
}
