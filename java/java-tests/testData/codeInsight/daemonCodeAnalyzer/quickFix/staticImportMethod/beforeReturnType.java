// "Import static method 'foo.B.aaaaaaa()'" "true-preview"
package foo;
public class X {
    {
        Integer i = <caret>aaaaaaa("", 10);
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