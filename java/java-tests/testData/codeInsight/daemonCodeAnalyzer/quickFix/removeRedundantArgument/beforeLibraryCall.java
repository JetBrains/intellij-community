// "Remove redundant argument to call 'trim()'" "true-preview"
class A {
  public A() {
    String s = "xyz".trim("<caret>123")
  }
}