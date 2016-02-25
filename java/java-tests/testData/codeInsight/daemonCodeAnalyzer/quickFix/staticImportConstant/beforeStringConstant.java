// "Static import constant 'foo.B.String'" "false"
package foo;

public class X {
    {
      ((String<caret>))
    }
}

class B {
  public static Object String = null;
}