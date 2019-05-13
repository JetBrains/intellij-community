// "Remove redundant arguments to call 'method(int, String)'" "true"
class A {
  public A() {
    method(5, "",//before arg to delete
    <caret> "a" + //in arg to delete
    "b"
    );//end line comment
  }

  private void method(int s, String s2) {
  }
}