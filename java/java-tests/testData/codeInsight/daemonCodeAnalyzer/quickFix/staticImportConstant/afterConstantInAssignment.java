// "Import static constant 'foo.B.aaaaaaa'" "true-preview"
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