// "Import static constant..." "true"

package foo;
import static foo.A.*;
import static foo.B.*;

public class X {
    {
      CON<caret>ST;
    }
}

class A {
  public static Object CONST = null;
}

class B {
  public static Object CONST = null;
}