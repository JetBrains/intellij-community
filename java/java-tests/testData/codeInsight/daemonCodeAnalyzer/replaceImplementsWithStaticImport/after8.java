// "Replace Implements with Static Import" "true"

import static I1.BAZZ;
import static II.FOO;

public class X {
  void bar() {
    System.out.println(FOO);
    System.out.println(BAZZ);
  }
}

interface II extends I1{
  String FOO = "foo";
}

interface I1 {
  String BAZZ = "bazz";
}
