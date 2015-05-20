// "Static import method 'foo.B.aaaaaaa'" "true"
package foo;

import static foo.B.aaaaaaa;

public class X {
    {
        foo(1, 2, aaaaaaa(""));
    }
  
    void foo(Integer... p) {}
}

class B {
  public static  Integer aaaaaaa(String s) {
    return 1;
  }
}
class B1 {
  public static String aaaaaaa(String s) {
    return "";
  }
}