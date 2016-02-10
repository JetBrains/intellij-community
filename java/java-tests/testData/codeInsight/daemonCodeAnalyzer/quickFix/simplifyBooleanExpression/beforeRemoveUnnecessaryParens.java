// "Simplify boolean expression" "true"
package test;

public class TestSimplifyIf
{
  void test() {
    String str = "";
    if ((<caret>false || str.equals("hello")) && !str.equals("foo")) {
      // Nothing
    }
  }
}