// "Import static constant..." "true"

package foo;
import static foo.A.*;
import static foo.A.CONST;
import static foo.B.*;

public class X {
    {
      CONST;
    }
}

class A {
  public static Object CONST = null;
}

class B {
  public static Object CONST = null;
}