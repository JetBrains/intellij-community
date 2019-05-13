// "Import static constant 'foo.B.K'" "false"
package foo;

public class X {

  {
    <caret>K = new X();
  }

}

class B {
  public static Object K = null;
}