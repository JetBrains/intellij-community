// "Static import constant..." "true"
package foo;

import static foo.B.aaaaaaa;

public class X {
    {
        aaaaaaa;
    }
}

class B {
  public static Integer aaaaaaa = 1;
}
class B1 {
  public static String aaaaaaa = "";
}