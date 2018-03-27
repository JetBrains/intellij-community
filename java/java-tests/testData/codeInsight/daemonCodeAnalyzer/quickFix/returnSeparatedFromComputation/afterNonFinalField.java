// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
  int k;

  int foo() {
    int n = k;
    if (k < 0) return -1;
    <caret>return n;
  }
}