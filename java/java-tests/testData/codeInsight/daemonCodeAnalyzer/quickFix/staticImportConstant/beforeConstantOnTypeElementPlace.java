// "Static import constant 'foo.B.K'" "false"
package foo;

public class X {

    <caret>K foo() {}

}

class B {
  public static Object K = null;
}