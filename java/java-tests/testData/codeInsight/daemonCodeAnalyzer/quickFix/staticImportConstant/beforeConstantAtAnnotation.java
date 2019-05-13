// "Import static constant 'foo.B.K'" "false"
package foo;

@<caret>K
public class X {
}

class B {
  public static Object K = null;
}