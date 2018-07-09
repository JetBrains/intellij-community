// "Create property" "false"
class JC {
  void setFoo(int a) {}
}

class Main {
  void usage(JC jc) {
    jc.<caret>getFoo();
  }
}
