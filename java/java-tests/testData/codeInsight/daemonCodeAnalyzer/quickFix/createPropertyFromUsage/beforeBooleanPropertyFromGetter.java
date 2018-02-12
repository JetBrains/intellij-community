// "Create property" "true"
class JC {}

class Main {
  void usage(JC jc) {
    jc.<caret>isFoo();
  }
}
