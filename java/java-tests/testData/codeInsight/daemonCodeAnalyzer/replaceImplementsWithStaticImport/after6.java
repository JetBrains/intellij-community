// "Replace Implements with Static Import" "true"

import static I.FOO;
import static I1.BAZZ;

public class X {
  void bar() {
    System.out.println(FOO);
    System.out.println(BAZZ);
  }
}

interface I extends I1{
  String FOO = "foo";
}

interface I1 {
  String BAZZ = "bazz";
}