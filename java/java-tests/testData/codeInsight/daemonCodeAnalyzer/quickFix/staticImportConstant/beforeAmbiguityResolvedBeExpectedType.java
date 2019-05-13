// "Import static constant 'foo.B.CONST'" "true"

package foo;
import static foo.A.*;
import static foo.B.*;

public class X {
    {
      int i = CON<caret>ST.length();
    }
}

class A {
  public static Object CONST = null;
}

class B {
  public static String CONST = null;
}