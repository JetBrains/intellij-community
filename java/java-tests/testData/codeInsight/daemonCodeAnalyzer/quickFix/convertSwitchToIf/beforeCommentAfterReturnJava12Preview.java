// "Replace 'switch' with 'if'" "true"
class Test {
  void f(int n, int k) {
    sw<caret>itch (n) {
      case 0 -> {
        if (k == 0) return; //this comment causes the exception IDEA-204717
        if (k == 1) {
        }
      }
    }
  }
}