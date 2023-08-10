// "Remove redundant assignment" "true-preview"
class Test {
  void foo() {
    String var;
    this.bar("someString");
  }

  void bar(String arg) {

  }
}
