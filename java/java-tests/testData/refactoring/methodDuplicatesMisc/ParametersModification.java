class Test {
  private double d<caret>d1(int index, double compVal, int numDays) {
    double aveSlope = 0;

    for (int i = 0; condition(index, numDays, i); i++) {
      --index;
      aveSlope = comminSending(index, compVal, aveSlope, i);
    }
    return aveSlope;
  }

  private double ss2(int index, double compVal, int numDays) {
    double aveSlope = 0;

    for (int i = 0; condition(index, numDays, i); i++) {
      --index;
      aveSlope = comminSending(index, compVal, aveSlope, i);
    }
    return aveSlope;
  }

  private boolean condition(int idx, int n, int i){return false;}
  private double comminSending(int idx, double d, double a, int i){return 0;}
}