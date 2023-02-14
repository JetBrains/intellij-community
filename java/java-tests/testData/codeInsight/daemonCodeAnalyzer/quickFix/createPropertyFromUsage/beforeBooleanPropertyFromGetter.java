// "Create property" "true-preview"
class JC {}

class Main {
  void usage(JC jc) {
    jc.<caret>isFoo();
  }
}
