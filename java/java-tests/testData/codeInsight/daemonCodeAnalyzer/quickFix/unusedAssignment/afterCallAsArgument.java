// "Remove redundant assignment" "true"
class Test {
  void foo() {
    String var;
    this.bar("someString");
  }

  void bar(String arg) {

  }
}
