// "Import static constant 'foo.B.aaaaaaa'" "true-preview"
package foo;

public class X {
    {
        int i = <caret>aaaaaaa;
    }
}

class B {
  public static Integer aaaaaaa = 1;
}
class B1 {
  public static String aaaaaaa = "";
}