// "Move 'return' closer to computation of the value of 'n'" "false"
class T {
  int f(boolean b) {
    int n = -1;
    if (b) {
      throw new RuntimeException();
    }
    r<caret>eturn n;
  }
}