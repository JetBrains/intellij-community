// "Create constructor" "false"
class Test {

  enum E {}

  void usage() {
    new E(<caret>"a");
  }
}
