// "Import static constant 'foo.B.CONST'" "true"

package foo;
import static foo.A.*;
import static foo.B.*;
import static foo.B.CONST;

public class X {
    {
      int i = CONST.length();
    }
}

class A {
  public static Object CONST = null;
}

class B {
  public static String CONST = null;
}