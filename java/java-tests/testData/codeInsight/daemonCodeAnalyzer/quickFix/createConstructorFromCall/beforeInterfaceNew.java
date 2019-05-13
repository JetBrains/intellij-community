// "Create constructor" "false"
class Test {

  interface I {}

  void usage() {
    new I(<caret>"a") {};
  }
}
