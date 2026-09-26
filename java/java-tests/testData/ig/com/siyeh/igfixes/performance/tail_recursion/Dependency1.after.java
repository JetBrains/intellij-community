class Dependency1 {

  public int factorial(int val) {
    return factorial(val, 1);
  }

  private int factorial(int val, int runningVal) {
      while (true) {
          if (val == 1) {
              return runningVal;
          } else {
              runningVal = runningVal * val;
              val = val - 1;
          }
      }
  }
}