// "Static import constant 'foo.B.aaaaaaa'" "true"
package foo;

import static foo.B.aaaaaaa;

public class X {
    {
        int i = aaaaaaa;
    }
}

class B {
  public static Integer aaaaaaa = 1;
}
class B1 {
  public static String aaaaaaa = "";
}