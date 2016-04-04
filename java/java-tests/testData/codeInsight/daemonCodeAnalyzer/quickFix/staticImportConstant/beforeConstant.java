// "Static import constant..." "true"
package foo;

public class X {
    {
        <caret>aaaaaaa;
    }
}

class B {
  public static Integer aaaaaaa = 1;
}
class B1 {
  public static String aaaaaaa = "";
}