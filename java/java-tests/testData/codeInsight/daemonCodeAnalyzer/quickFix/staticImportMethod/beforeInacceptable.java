// "Static import method 'foo.B.aaaaaaa'" "false"
package foo;

public class X {
    {
        foo(1, 2, <caret>aaaaaaa(""));
    }
  
    void foo(Integer... p) {}
}

class B {
  @Deprecated
  public static  Integer aaaaaaa(String s, String b) {
    return 1;
  }
}
class B1 {
  public static String aaaaaaa(String s) {
    return "";
  }
}