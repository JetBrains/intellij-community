// "Remove redundant assignment" "true-preview"
class Test {
  void foo() {
    String var;
    this.bar(v<caret>ar = "someString");
  }

  void bar(String arg) {

  }
}
