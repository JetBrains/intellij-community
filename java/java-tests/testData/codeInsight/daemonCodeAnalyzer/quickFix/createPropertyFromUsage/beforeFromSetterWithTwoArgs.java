// "Create property" "false"
class JC {}

class Main {
  void usage(JC jc) {
    jc.<caret>setFoo("1", "2");
  }
}
