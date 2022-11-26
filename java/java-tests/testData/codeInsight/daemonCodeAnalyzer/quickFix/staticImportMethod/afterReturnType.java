// "Import static method 'foo.B.aaaaaaa()'" "true-preview"
package foo;

import static foo.B.aaaaaaa;

public class X {
    {
        Integer i = aaaaaaa("", 10);
    }
}

class B {
  public static  Integer aaaaaaa(String s, int i) {
    return 1;
  }
}
class B1 {
  public static String aaaaaaa(String s, int i) {
    return "";
  }
}