// "Simplify boolean expression" "true-preview"
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