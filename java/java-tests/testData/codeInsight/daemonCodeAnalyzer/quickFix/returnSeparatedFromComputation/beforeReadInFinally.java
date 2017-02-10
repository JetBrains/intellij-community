// "Move 'return' closer to computation of the value of 'n'" "false"
class T {
  int f(boolean b) {
    int n = -1;
    try {
      if (b) throw new RuntimeException();
      n = 1;
      re<caret>turn n;
    }
    finally {
      System.out.println(n);
    }
  }
}