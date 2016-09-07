// "Move 'return' closer to computation of the value of 'r'" "false"
class T {
  int[] f(boolean b) {
    int[] r = new int[]{-1};
    if (b) {
      r[0] = 1;
    }
    re<caret>turn r;
  }
}