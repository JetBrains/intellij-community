// "Create property" "false"
class JC {
  void setFoo(boolean a) {}
}

class Main {
  void usage(JC jc) {
    jc.<caret>isFoo();
  }
}
