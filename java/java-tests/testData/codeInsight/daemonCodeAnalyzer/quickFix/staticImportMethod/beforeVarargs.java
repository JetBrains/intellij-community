// "Import static method 'foo.B.aaaaaaa()'" "true-preview"
package foo;

public class X {
    {
        foo(1, 2, <caret>aaaaaaa(""));
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