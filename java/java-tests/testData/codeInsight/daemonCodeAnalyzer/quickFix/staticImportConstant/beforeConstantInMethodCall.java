// "Import static constant 'foo.B.String'" "false"
package foo;

public class X {
    {
      ti<caret>mes(42);
    }
}

class B {
  public static int times = 42;
}